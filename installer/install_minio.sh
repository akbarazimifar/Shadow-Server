#!/bin/bash

. ./lib.sh

function download_sticker_pack
{
    PACK_ID=$1
    PACK_MAX_ID=$2

    mkdir -p ${DATA_PATH}/stickers/$PACK_ID/full

    cd ${DATA_PATH}/stickers/$PACK_ID

    wget https://cdn.signal.org/stickers/$PACK_ID/manifest.proto --no-check-certificate

    cd ${DATA_PATH}/stickers/$PACK_ID/full

    for i in $(seq 1 $PACK_MAX_ID)
       do
          wget https://cdn.signal.org/stickers/$PACK_ID/full/$i --no-check-certificate
       done
}


# -------- MAIN ------------

SERVER_DOMAIN=$1

mkdir ${MINIO_PATH}
cp shadow.json ${MINIO_PATH}
sed -i "s|/home/shadow/data|${DATA_PATH}|" minio.service
sed -i "s/=shadow/=${USER_SH}/" minio.service
cp minio.service /etc/systemd/system/
cd ${MINIO_PATH}

printf "\nDownloading Minio..."
wget https://dl.min.io/server/minio/release/linux-amd64/minio

MINIO_ADMIN_LOGIN="minio_admin"
MINIO_SERVICE_LOGIN="minio_shadow"

echo "Enter the secret key (password) which will be used for Minio admin access >>"
read -r MINIO_ADMIN_PASSWORD

if [ -z "$MINIO_ADMIN_PASSWORD" ]
    then 
        error_quit "Entered key is empty"
    fi

echo "Enter the secret key (password) which will be used by the Shadow system components to access the Minio service >>"
read -r MINIO_SERVICE_PASSWORD

if [ -z "$MINIO_SERVICE_PASSWORD" ]
    then 
        error_quit "Entered key is empty"
    fi

# Update config

MINIO_ADMIN_PASSWORD_CONV=$(normalize_bash $MINIO_ADMIN_PASSWORD)
MINIO_SERVICE_PASSWORD_CONV=$(normalize_bash $MINIO_SERVICE_PASSWORD)
MINIO_SERVICE_PASSWORD_CONV2=$(preproc_sed $(normalize_yaml $(preproc_cfg $MINIO_SERVICE_PASSWORD)))
sed -i "s/accessSecret\: your_service_password/accessSecret\: '${MINIO_SERVICE_PASSWORD_CONV2}'/" ${SERVER_PATH}/config/shadow.yml

echo "export MINIO_ROOT_USER=$MINIO_ADMIN_LOGIN" >> ${USER_PATH}/.bashrc
echo "export MINIO_ROOT_PASSWORD=$(normalize_bash $MINIO_ADMIN_PASSWORD)" >> ${USER_PATH}/.bashrc
echo "export MINIO_BROWSER=off" >> ${USER_PATH}/.bashrc

echo "Copying credentials.."

mkdir -p ${USER_PATH}/.minio/certs/CAs
cp ${SERVER_PATH}/cloud_a.crt ${USER_PATH}/.minio/certs/public.crt
cp ${SERVER_PATH}/cloud_a.key ${USER_PATH}/.minio/certs/private.key
cp ${SERVER_PATH}/rootCA.crt ${USER_PATH}/.minio/certs/CAs/

echo "Opening port 9000..."

firewall-cmd --zone=public --permanent --add-port=9000/tcp
firewall-cmd --reload

echo "Downloading Minio client..."

wget https://dl.min.io/client/mc/release/linux-amd64/mc

mkdir ${DATA_PATH}
chown -R ${USER_SH} ${DATA_PATH}
chown -R ${USER_SH} ${MINIO_PATH} 
chown -R ${USER_SH} ${USER_PATH}/.minio
chmod +x minio
chmod +x mc
mv minio /usr/local/bin/minio
restorecon -rv /usr/local/bin/

echo "Creating Minio environment file..."

echo "MINIO_ROOT_USER=$MINIO_ADMIN_LOGIN" > /etc/default/minio_env
echo "MINIO_ROOT_PASSWORD=$MINIO_ADMIN_PASSWORD_CONV" >> /etc/default/minio_env
echo "MINIO_BROWSER=off" >> /etc/default/minio_env

echo "Creating Minio service..."

systemctl daemon-reload
systemctl enable minio.service

echo "Starting Minio service..."

systemctl start minio.service

echo "Creating Minio config file..."

sleep 5
su -c "${MINIO_PATH}/mc alias set shadow https://localhost:9000 ${MINIO_ADMIN_LOGIN} ${MINIO_ADMIN_PASSWORD_CONV}" - ${USER_SH}

echo "Creating buckets..."

su -c "${MINIO_PATH}/mc mb shadow/attachments" - ${USER_SH}
su -c "${MINIO_PATH}/mc mb shadow/profiles" - ${USER_SH}
su -c "${MINIO_PATH}/mc mb shadow/stickers" - ${USER_SH}
su -c "${MINIO_PATH}/mc mb shadow/debuglogs" - ${USER_SH}

echo "Setting up policies..."

su -c "${MINIO_PATH}/mc policy set download shadow/attachments" - ${USER_SH}
su -c "${MINIO_PATH}/mc policy set download shadow/profiles" - ${USER_SH}
su -c "${MINIO_PATH}/mc policy set download shadow/stickers" - ${USER_SH}
su -c "${MINIO_PATH}/mc policy set download shadow/debuglogs" - ${USER_SH}

echo "Assigning the service user..."

su -c "${MINIO_PATH}/mc admin user add shadow ${MINIO_SERVICE_LOGIN} ${MINIO_SERVICE_PASSWORD_CONV}" - ${USER_SH}

echo "Setting up the access policy..."

su -c "${MINIO_PATH}/mc admin policy add shadow shadow_policy ${MINIO_PATH}/shadow.json" - ${USER_SH}
su -c "${MINIO_PATH}/mc admin policy set shadow shadow_policy user=${MINIO_SERVICE_LOGIN}" - ${USER_SH}

echo "Downloading stickers..."

download_sticker_pack fb535407d2f6497ec074df8b9c51dd1d 25
download_sticker_pack 9acc9e8aba563d26a4994e69263e3b25 24
download_sticker_pack e61fa0867031597467ccc036cc65d403 29
download_sticker_pack cca32f5b905208b7d0f1e17f23fdc185 89
download_sticker_pack cfc50156556893ef9838069d3890fe49 23

chown -R ${USER_SH} ${DATA_PATH}/stickers/

# Update Shadow server config

if test -f ${SERVER_PATH}/config/shadow.yml
    then      
       sed -i "s|cloudUri\: https\://shadow.example.com|cloudUri\: https\://${SERVER_DOMAIN}|" ${SERVER_PATH}/config/shadow.yml
    fi
    