#!/bin/sh
cat > /tmp/users.acl <<EOF
user default off
user ${REDIS_USERNAME} on >${REDIS_PASSWORD} ~* &* +@all
EOF

exec redis-server --requirepass "${REDIS_PASSWORD}" --aclfile /tmp/users.acl
