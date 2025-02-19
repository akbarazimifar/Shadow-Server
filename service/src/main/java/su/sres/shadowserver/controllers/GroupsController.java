/*
 * Copyright 2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package su.sres.shadowserver.controllers;

import com.codahale.metrics.annotation.Timed;
import com.google.protobuf.ByteString;
import io.dropwizard.auth.Auth;
import io.dropwizard.util.Strings;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import su.sres.shadowserver.auth.ExternalGroupCredentialGenerator;
import su.sres.shadowserver.auth.GroupUser;
import su.sres.shadowserver.configuration.GroupConfiguration;
import su.sres.shadowserver.groups.GroupAuth;
import su.sres.shadowserver.groups.GroupChangeApplicator;
import su.sres.shadowserver.groups.GroupValidator;
import su.sres.shadowserver.providers.NoUnknownFields;
import su.sres.shadowserver.providers.ProtocolBufferMediaType;
import su.sres.shadowserver.s3.PolicySigner;
import su.sres.shadowserver.s3.PostPolicyGenerator;
import su.sres.shadowserver.storage.GroupsManager;
import su.sres.shadowserver.groups.protos.AccessControl;
import su.sres.shadowserver.groups.protos.AvatarUploadAttributes;
import su.sres.shadowserver.groups.protos.ExternalGroupCredential;
import su.sres.shadowserver.groups.protos.Group;
import su.sres.shadowserver.groups.protos.GroupChange;
import su.sres.shadowserver.groups.protos.GroupChange.Actions;
import su.sres.shadowserver.groups.protos.GroupChanges;
import su.sres.shadowserver.groups.protos.GroupJoinInfo;
import su.sres.shadowserver.groups.protos.Member;
import su.sres.shadowserver.groups.protos.MemberPendingAdminApproval;
import su.sres.shadowserver.groups.protos.MemberPendingProfileKey;
import su.sres.shadowserver.util.CollectionUtil;
import su.sres.shadowserver.util.Pair;
import org.signal.zkgroup.NotarySignature;
import org.signal.zkgroup.ServerSecretParams;
import org.signal.zkgroup.profiles.ServerZkProfileOperations;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/v1/groups")
public class GroupsController {
  private static final int LOG_VERSION_LIMIT = 64;
  private static final int INVITE_LINKS_CHANGE_EPOCH = 1;
  private static final int DESCRIPTION_CHANGE_EPOCH = 2;
  private static final int ANNOUNCEMENTS_ONLY_CHANGE_EPOCH = 3;

  private final GroupsManager groupsManager;
  private final ServerSecretParams serverSecretParams;
  private final GroupValidator groupValidator;
  private final GroupChangeApplicator groupChangeApplicator;

  private final PolicySigner policySigner;
  private final PostPolicyGenerator policyGenerator;

  private final ExternalGroupCredentialGenerator externalGroupCredentialGenerator;

  public GroupsController(GroupsManager groupsManager,
      ServerSecretParams serverSecretParams,
      PolicySigner policySigner,
      PostPolicyGenerator policyGenerator,
      GroupConfiguration groupConfiguration,
      ExternalGroupCredentialGenerator externalGroupCredentialGenerator) {
    this.groupsManager = groupsManager;
    this.serverSecretParams = serverSecretParams;
    this.groupValidator = new GroupValidator(new ServerZkProfileOperations(serverSecretParams), groupConfiguration);
    this.groupChangeApplicator = new GroupChangeApplicator(this.groupValidator);
    this.policySigner = policySigner;
    this.policyGenerator = policyGenerator;
    this.externalGroupCredentialGenerator = externalGroupCredentialGenerator;
  }

  @Timed
  @GET
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  public Response getGroup(@Auth GroupUser user) {

    Optional<Group> group = groupsManager.getGroup(user.getGroupId());

    if (group.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    if (GroupAuth.isMember(user, group.get()) || GroupAuth.isMemberPendingProfileKey(user, group.get())) {
      return Response.ok(group.get()).build();
    } else {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
  }

  @Timed
  @GET
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Path("/join/{inviteLinkPassword: [^/]*}")
  public Response getGroupJoinInfo(@Auth GroupUser user, @PathParam("inviteLinkPassword") String inviteLinkPasswordString) {
    final byte[] inviteLinkPassword;
    if (Strings.isNullOrEmpty(inviteLinkPasswordString)) {
      inviteLinkPassword = null;
    } else {
      inviteLinkPassword = Base64.decodeBase64(inviteLinkPasswordString);
    }

    Optional<Group> group = groupsManager.getGroup(user.getGroupId());

    if (group.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    final AccessControl.AccessRequired accessRequired = group.get().getAccessControl().getAddFromInviteLink();
    final boolean pendingAdminApproval = GroupAuth.isMemberPendingAdminApproval(user, group.get());
    if (!pendingAdminApproval) {
      if (!MessageDigest.isEqual(inviteLinkPassword, group.get().getInviteLinkPassword().toByteArray())) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }

      if (accessRequired == AccessControl.AccessRequired.UNSATISFIABLE || accessRequired == AccessControl.AccessRequired.UNKNOWN) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }
    }

    GroupJoinInfo.Builder groupJoinInfoBuilder = GroupJoinInfo.newBuilder();
    groupJoinInfoBuilder.setPublicKey(group.get().getPublicKey());
    groupJoinInfoBuilder.setTitle(group.get().getTitle());
    groupJoinInfoBuilder.setDescription(group.get().getDescription());
    groupJoinInfoBuilder.setAvatar(group.get().getAvatar());
    groupJoinInfoBuilder.setMemberCount(group.get().getMembersCount());
    groupJoinInfoBuilder.setAddFromInviteLink(accessRequired);
    groupJoinInfoBuilder.setVersion(group.get().getVersion());
    groupJoinInfoBuilder.setPendingAdminApproval(pendingAdminApproval);
    groupJoinInfoBuilder.setPendingAdminApprovalFull(groupValidator.isPendingAdminApprovalFull(group.get()));
    return Response.ok(groupJoinInfoBuilder.build()).build();
  }

  @Timed
  @GET
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Path("/logs/{fromVersion}")
  public Response getGroupLogs(@Auth GroupUser user, @PathParam("fromVersion") int fromVersion) {

    Optional<Group> group = groupsManager.getGroup(user.getGroupId());

    if (group.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    Optional<Member> member = GroupAuth.getMember(user, group.get());

    if (member.isEmpty()) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    if (member.get().getJoinedAtVersion() > fromVersion) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    final int latestGroupVersion = group.get().getVersion();

    if (latestGroupVersion + 1 <= fromVersion) {
      return Response.ok(GroupChanges.newBuilder().build()).build();
    }

    if (latestGroupVersion + 1 - fromVersion > LOG_VERSION_LIMIT) {

      List<GroupChanges.GroupChangeState> records = groupsManager.getChangeRecords(user.getGroupId(), group.get(), fromVersion, fromVersion + LOG_VERSION_LIMIT);

      return Response.status(HttpStatus.SC_PARTIAL_CONTENT)
          .header(HttpHeaders.CONTENT_RANGE, String.format(Locale.US, "versions %d-%d/%d", fromVersion, fromVersion + LOG_VERSION_LIMIT - 1, latestGroupVersion))
          .entity(GroupChanges.newBuilder()
              .addAllGroupChanges(records)
              .build())
          .build();
    } else {

      List<GroupChanges.GroupChangeState> records = groupsManager.getChangeRecords(user.getGroupId(), group.get(), fromVersion, latestGroupVersion + 1);

      return Response.ok(GroupChanges.newBuilder()
          .addAllGroupChanges(records)
          .build())
          .build();
    }
  }

  @Timed
  @GET
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Path("/avatar/form")
  public AvatarUploadAttributes getAvatarUploadForm(@Auth GroupUser user) {
    byte[] object = new byte[16];
    new SecureRandom().nextBytes(object);

    String objectName = "groups/" + Base64.encodeBase64URLSafeString(user.getGroupId().toByteArray()) + "/" + Base64.encodeBase64URLSafeString(object);
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    Pair<String, String> policy = policyGenerator.createFor(now, objectName, 3 * 1024 * 1024);
    String signature = policySigner.getSignature(now, policy.second());

    return AvatarUploadAttributes.newBuilder()
        .setKey(objectName)
        .setCredential(policy.first())
        .setAcl("private")
        .setAlgorithm("AWS4-HMAC-SHA256")
        .setDate(now.format(PostPolicyGenerator.AWS_DATE_TIME))
        .setPolicy(policy.second())
        .setSignature(signature)
        .build();
  }

  @Timed
  @PUT
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Consumes(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  public Response createGroup(@Auth GroupUser user, @NoUnknownFields Group group) {
    if (group.getVersion() != 0)
      return Response.status(Response.Status.BAD_REQUEST).build();
    if (group.getPublicKey() == null || group.getPublicKey().isEmpty())
      return Response.status(Response.Status.BAD_REQUEST).build();
    if (group.getTitle() == null || group.getTitle().isEmpty())
      return Response.status(Response.Status.BAD_REQUEST).build();

    if (group.getAccessControl().getAttributes() == AccessControl.AccessRequired.UNKNOWN ||
        group.getAccessControl().getAttributes() == AccessControl.AccessRequired.UNRECOGNIZED ||
        group.getAccessControl().getMembers() == AccessControl.AccessRequired.UNKNOWN ||
        group.getAccessControl().getMembers() == AccessControl.AccessRequired.UNRECOGNIZED) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    if (!MessageDigest.isEqual(user.getGroupPublicKey().serialize(), group.getPublicKey().toByteArray())) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    if (!groupValidator.isValidAvatarUrl(group.getAvatar(), user.getGroupId())) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    List<Member> validatedMembers = new LinkedList<>();
    List<MemberPendingProfileKey> validatedMemberPendingProfileKeys = new LinkedList<>();

    for (Member member : group.getMembersList()) {
      validatedMembers.add(groupValidator.validateMember(group, member));
    }

    group = group.toBuilder().clearMembers().addAllMembers(validatedMembers).build();

    Optional<Member> source = GroupAuth.getMember(user, group);

    if (source.isEmpty() || source.get().getRole() != Member.Role.ADMINISTRATOR) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    for (MemberPendingProfileKey memberPendingProfileKey : group.getMembersPendingProfileKeyList()) {
      validatedMemberPendingProfileKeys.add(groupValidator.validateMemberPendingProfileKey(source.get(), group, memberPendingProfileKey));
    }

    group = group.toBuilder().clearMembersPendingProfileKey().addAllMembersPendingProfileKey(validatedMemberPendingProfileKeys).build();

    Stream<ByteString> memberUserIds = group.getMembersList().stream().map(Member::getUserId);
    Stream<ByteString> membersPendingProfileKeyUserIds = group.getMembersPendingProfileKeyList().stream().map(memberPendingProfileKey -> memberPendingProfileKey.getMember().getUserId());

    if (CollectionUtil.containsDuplicates(Stream.concat(memberUserIds, membersPendingProfileKeyUserIds).collect(Collectors.toList()))) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    if (group.getMembersPendingAdminApprovalCount() > 0) {
      throw new BadRequestException("cannot create a group with already pending members");
    }

    final Group validatedGroup = group;
    final GroupChange initialGroupChange = GroupChange.newBuilder()
        .setActions(Actions.newBuilder()
            .setVersion(0)
            .setSourceUuid(source.get().getUserId())
            .build().toByteString())
        .build();

    groupValidator.validateFinalGroupState(validatedGroup);

    boolean created = groupsManager.createGroup(user.getGroupId(), validatedGroup);

    boolean result;

    if (!created)
      result = false;
    else
      result = groupsManager.appendChangeRecord(user.getGroupId(), 0, initialGroupChange, validatedGroup);

    if (result)
      return Response.ok().build();
    else
      return Response.status(Response.Status.CONFLICT).build();
  }

  @Timed
  @PATCH
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Consumes(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  public Response modifyGroup(@Auth GroupUser user, @QueryParam("inviteLinkPassword") String inviteLinkPasswordString, @NoUnknownFields GroupChange.Actions submittedActions) {
    final byte[] inviteLinkPassword;
    if (Strings.isNullOrEmpty(inviteLinkPasswordString)) {
      inviteLinkPassword = null;
    } else {
      inviteLinkPassword = Base64.decodeBase64(inviteLinkPasswordString);
    }

    Optional<Group> group = groupsManager.getGroup(user.getGroupId());

    if (group.isEmpty()) {
      throw new BadRequestException("No such group exists");
    }

    if (group.get().getVersion() >= submittedActions.getVersion() || group.get().getVersion() != submittedActions.getVersion() - 1) {
      return Response.status(Response.Status.CONFLICT).entity(group.get()).build();
    }

    Actions actions = submittedActions.toBuilder()
        .clearAddMembers()
        .addAllAddMembers(groupValidator.validateAddMember(user, inviteLinkPassword, group.get(), submittedActions.getAddMembersList()))
        .clearAddMembersPendingProfileKey()
        .addAllAddMembersPendingProfileKey(groupValidator.validateAddMembersPendingProfileKey(user, group.get(), submittedActions.getAddMembersPendingProfileKeyList()))
        .clearAddMembersPendingAdminApproval()
        .addAllAddMembersPendingAdminApproval(groupValidator.validateAddMembersPendingAdminApproval(user, inviteLinkPassword, group.get(), submittedActions.getAddMembersPendingAdminApprovalList()))
        .build();

    int changeEpoch = 0;

    Group.Builder modifiedGroupBuilder = group.get().toBuilder();

    groupChangeApplicator.applyAddMembers(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getAddMembersList());
    groupChangeApplicator.applyDeleteMembers(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getDeleteMembersList());
    groupChangeApplicator.applyModifyMemberRoles(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyMemberRolesList());
    groupChangeApplicator.applyModifyMemberProfileKeys(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyMemberProfileKeysList());

    groupChangeApplicator.applyAddMembersPendingProfileKey(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getAddMembersPendingProfileKeyList());
    groupChangeApplicator.applyDeleteMembersPendingProfileKey(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getDeleteMembersPendingProfileKeyList());
    groupChangeApplicator.applyPromoteMembersPendingProfileKey(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getPromoteMembersPendingProfileKeyList());

    if (actions.hasModifyTitle())
      groupChangeApplicator.applyModifyTitle(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyTitle());
    if (actions.hasModifyAvatar())
      groupChangeApplicator.applyModifyAvatar(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyAvatar());
    if (actions.hasModifyDisappearingMessageTimer())
      groupChangeApplicator.applyModifyDisappearingMessageTimer(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyDisappearingMessageTimer());

    if (actions.hasModifyAttributesAccess())
      groupChangeApplicator.applyModifyAttributesAccess(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyAttributesAccess());
    if (actions.hasModifyMemberAccess())
      groupChangeApplicator.applyModifyMembersAccess(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyMemberAccess());

    if (actions.hasModifyAddFromInviteLinkAccess()) {
      groupChangeApplicator.applyModifyAddFromInviteLinkAccess(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyAddFromInviteLinkAccess());
      changeEpoch = Math.max(changeEpoch, INVITE_LINKS_CHANGE_EPOCH);
    }
    if (actions.getAddMembersPendingAdminApprovalCount() != 0) {
      groupChangeApplicator.applyAddMembersPendingAdminApproval(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getAddMembersPendingAdminApprovalList());
      changeEpoch = Math.max(changeEpoch, INVITE_LINKS_CHANGE_EPOCH);
    }
    if (actions.getDeleteMembersPendingAdminApprovalCount() != 0) {
      groupChangeApplicator.applyDeleteMembersPendingAdminApproval(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getDeleteMembersPendingAdminApprovalList());
      changeEpoch = Math.max(changeEpoch, INVITE_LINKS_CHANGE_EPOCH);
    }
    if (actions.getPromoteMembersPendingAdminApprovalCount() != 0) {
      groupChangeApplicator.applyPromotePendingAdminApproval(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getPromoteMembersPendingAdminApprovalList());
      changeEpoch = Math.max(changeEpoch, INVITE_LINKS_CHANGE_EPOCH);
    }
    if (actions.hasModifyInviteLinkPassword()) {
      groupChangeApplicator.applyModifyInviteLinkPassword(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyInviteLinkPassword());
      changeEpoch = Math.max(changeEpoch, INVITE_LINKS_CHANGE_EPOCH);
    }
    if (actions.hasModifyDescription()) {
      groupChangeApplicator.applyModifyDescription(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyDescription());
      changeEpoch = Math.max(changeEpoch, DESCRIPTION_CHANGE_EPOCH);
    }
    if (actions.hasModifyAnnouncementsOnly()) {
      groupChangeApplicator.applyModifyAnnouncementsOnly(user, inviteLinkPassword, group.get(), modifiedGroupBuilder, actions.getModifyAnnouncementsOnly());
      changeEpoch = Math.max(changeEpoch, ANNOUNCEMENTS_ONLY_CHANGE_EPOCH);
    }

    ByteString sourceUuid = Stream.of((Supplier<Optional<ByteString>>) () -> GroupAuth.getMember(user, group.get()).map(Member::getUserId),
        (Supplier<Optional<ByteString>>) () -> GroupAuth.getMember(user, modifiedGroupBuilder.build()).map(Member::getUserId),
        (Supplier<Optional<ByteString>>) () -> GroupAuth.getMemberPendingProfileKey(user, group.get()).map(pending -> pending.getMember().getUserId()),
        (Supplier<Optional<ByteString>>) () -> GroupAuth.getMemberPendingAdminApproval(user, group.get()).map(MemberPendingAdminApproval::getUserId),
        (Supplier<Optional<ByteString>>) () -> GroupAuth.getMemberPendingAdminApproval(user, modifiedGroupBuilder.build()).map(MemberPendingAdminApproval::getUserId))
        .map(Supplier::get)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .orElseThrow(ForbiddenException::new);

    actions = actions.toBuilder().setSourceUuid(sourceUuid).build();

    byte[] serializedActions = actions.toByteArray();
    int version = actions.getVersion();
    NotarySignature signature = serverSecretParams.sign(serializedActions);
    GroupChange signedGroupChange = GroupChange.newBuilder()
        .setActions(ByteString.copyFrom(serializedActions))
        .setServerSignature(ByteString.copyFrom(signature.serialize()))
        .setChangeEpoch(changeEpoch)
        .build();
    Group updatedGroupState = modifiedGroupBuilder.setVersion(version).build();

    groupValidator.validateFinalGroupState(updatedGroupState);

    Optional<Group> result = groupsManager.updateGroup(user.getGroupId(), updatedGroupState);

    if (result.isPresent()) {
      return Response.status(Response.Status.CONFLICT).entity(result.get()).build();
    }

    groupsManager.appendChangeRecord(user.getGroupId(), version, signedGroupChange, updatedGroupState);

    return Response.ok(signedGroupChange).build();

  }

  @Timed
  @GET
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Path("/token")
  public Response getToken(@Auth GroupUser user) {

    Optional<Group> group = groupsManager.getGroup(user.getGroupId());

    if (group.isEmpty()) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    Optional<Member> member = GroupAuth.getMember(user, group.get());

    if (member.isPresent()) {
      String token = externalGroupCredentialGenerator.generateFor(member.get().getUserId(), user.getGroupId());
      ExternalGroupCredential credential = ExternalGroupCredential.newBuilder().setToken(token).build();

      return Response.ok(credential).build();
    } else {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
  }
}
