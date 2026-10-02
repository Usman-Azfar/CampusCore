#!/bin/sh
# Starts CampusCore on Tomcat inside the container.
set -e

# Hosting platforms such as Render tell the app which port to listen on in $PORT
PORT="${PORT:-8080}"
sed -i "s/port=\"8080\"/port=\"$PORT\"/" /usr/local/tomcat/conf/server.xml

# Placeholder payment proof referenced by the demo data (the upload folder starts empty)
mkdir -p "$CMS_UPLOAD_DIR/proofs"
[ -f "$CMS_UPLOAD_DIR/proofs/demo-proof.png" ] || cp /opt/campuscore/demo-proof.png "$CMS_UPLOAD_DIR/proofs/demo-proof.png"

exec catalina.sh run
