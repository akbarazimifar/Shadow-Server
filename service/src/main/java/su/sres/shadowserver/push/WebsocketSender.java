/*
 * Copyright (C) 2014 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package su.sres.shadowserver.push;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.protobuf.ByteString;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.storage.Device;
import su.sres.shadowserver.storage.MessagesManager;
import su.sres.shadowserver.storage.PubSubManager;
import su.sres.shadowserver.util.Constants;
import su.sres.shadowserver.websocket.ProvisioningAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codahale.metrics.MetricRegistry.name;
import static su.sres.shadowserver.entities.MessageProtos.Envelope;
import static su.sres.shadowserver.storage.PubSubProtos.PubSubMessage;

public class WebsocketSender {

    public enum Type {
	APN,
	GCM,
	WEB
    }

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(WebsocketSender.class);

    private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);

    private final Meter websocketOnlineMeter = metricRegistry.meter(name(getClass(), "ws_online"));
    private final Meter websocketOfflineMeter = metricRegistry.meter(name(getClass(), "ws_offline"));

    private final Meter apnOnlineMeter = metricRegistry.meter(name(getClass(), "apn_online"));
    private final Meter apnOfflineMeter = metricRegistry.meter(name(getClass(), "apn_offline"));

    private final Meter gcmOnlineMeter = metricRegistry.meter(name(getClass(), "gcm_online"));
    private final Meter gcmOfflineMeter = metricRegistry.meter(name(getClass(), "gcm_offline"));

    private final Meter provisioningOnlineMeter = metricRegistry.meter(name(getClass(), "provisioning_online"));
    private final Meter provisioningOfflineMeter = metricRegistry.meter(name(getClass(), "provisioning_offline"));

    private final Counter ephemeralOnlineCounter = Metrics.counter(name(getClass(), "ephemeral"), "online", "true");
    private final Counter ephemeralOfflineCounter = Metrics.counter(name(getClass(), "ephemeral"), "offline", "true");

    private final MessagesManager messagesManager;
    private final PubSubManager pubSubManager;
    private final ClientPresenceManager clientPresenceManager;

    public WebsocketSender(MessagesManager messagesManager, PubSubManager pubSubManager, ClientPresenceManager clientPresenceManager) {
	this.messagesManager = messagesManager;
	this.pubSubManager = pubSubManager;
	this.clientPresenceManager = clientPresenceManager;
    }

    public boolean sendMessage(Account account, Device device, Envelope message, Type channel, boolean online) {
	final boolean clientPresent = clientPresenceManager.isPresent(account.getUuid(), device.getId());

	if (online) {
	    if (clientPresent) {
		ephemeralOnlineCounter.increment();
		messagesManager.insertEphemeral(account.getUuid(), device.getId(), message);
		return true;
	    } else {
		ephemeralOfflineCounter.increment();

		return false;
	    }
	} else {
	    messagesManager.insert(account.getUuid(), device.getId(), message);

	    if (clientPresent) {
		if (channel == Type.APN)
		    apnOnlineMeter.mark();
		else if (channel == Type.GCM)
		    gcmOnlineMeter.mark();
		else
		    websocketOnlineMeter.mark();

		return true;
	    } else {
		if (channel == Type.APN)
		    apnOfflineMeter.mark();
		else if (channel == Type.GCM)
		    gcmOfflineMeter.mark();
		else
		    websocketOfflineMeter.mark();

		return false;
	    }
	}
    }

    public boolean sendProvisioningMessage(ProvisioningAddress address, byte[] body) {
	PubSubMessage pubSubMessage = PubSubMessage.newBuilder()
		.setType(PubSubMessage.Type.DELIVER)
		.setContent(ByteString.copyFrom(body))
		.build();

	if (pubSubManager.publish(address, pubSubMessage)) {
	    provisioningOnlineMeter.mark();
	    return true;
	} else {
	    provisioningOfflineMeter.mark();
	    return false;
	}
    }
}
