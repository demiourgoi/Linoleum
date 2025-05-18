#/usr/bin/env bash

set -e -u

# This is not a mongosh script as in https://www.mongodb.com/docs/mongodb-shell/write-scripts/#execute-a-script-from-within-mongosh, but a shell session running commands on mongosh

# https://www.mongodb.com/docs/manual/core/timeseries/timeseries-quick-start/#std-label-timeseries-quick-start
# https://www.mongodb.com/docs/manual/core/timeseries-collections/
# https://www.mongodb.com/docs/v7.0/core/timeseries/timeseries-granularity/
# - Time: when the data point was recorded.
# - Metadata (sometimes referred to as source), which is a label or tag that identifies a data series and rarely changes. Metadata is stored in a metaField. You cannot add metaFields to time series documents after you create them
# - Granularity: by setting granularity, you control how frequently data is bucketed based on the ingestion rate of your data
# - Measurements (sometimes referred to as metrics or values), which are the data points tracked at increments in time. Generally these are key-value pairs that change over time.
MONGO_PORT=${MONGO_PORT:-27017}
MONGO_HOST=${MONGO_HOST:-localhost}

echo "Using MONGO_PORT=[${MONGO_PORT}], MONGO_HOST=[${MONGO_HOST}]"

mongosh --host ${MONGO_HOST} --port ${MONGO_PORT} <<END
use linoleum
db.createCollection(
   "evaluatedTraces",
   {
      timeseries: {
         timeField: "date",
         metaField: "formulaName",
         granularity: "seconds"
      }
   })
exit
END

echo
echo "Server initialized with success"