#!/bin/bash
# Generates a self-signed cert for LAN-only use so nginx has something to
# start with. Replace with your unit's internal-CA cert before real use —
# browsers will show a warning for a self-signed cert (expected on a LAN).
set -euo pipefail
cd "$(dirname "$0")"
openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
  -keyout battalion-portal.key -out battalion-portal.crt \
  -subj "/C=IN/ST=NA/L=NA/O=15 Rajputana Rifles/CN=battalion-portal.local"
echo "Generated battalion-portal.crt / battalion-portal.key in $(pwd)"
