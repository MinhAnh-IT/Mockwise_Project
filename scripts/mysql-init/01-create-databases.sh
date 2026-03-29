#!/bin/bash
set -e

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE DATABASE IF NOT EXISTS iam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE DATABASE IF NOT EXISTS user_profile CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE DATABASE IF NOT EXISTS billing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE DATABASE IF NOT EXISTS question_bank CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

    GRANT ALL PRIVILEGES ON iam.* TO '${MYSQL_USER}'@'%';
    GRANT ALL PRIVILEGES ON user_profile.* TO '${MYSQL_USER}'@'%';
    GRANT ALL PRIVILEGES ON billing.* TO '${MYSQL_USER}'@'%';
    GRANT ALL PRIVILEGES ON question_bank.* TO '${MYSQL_USER}'@'%';
    FLUSH PRIVILEGES;
EOSQL
