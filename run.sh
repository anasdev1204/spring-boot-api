set -a
source .env.dev
set +a

mvn spotless:apply
mvn clean verify