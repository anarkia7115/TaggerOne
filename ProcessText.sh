CP=libs/taggerOne.jar
CP=${CP}:libs/trove-3.0.3.jar
CP=${CP}:libs/dragontool.jar
CP=${CP}:libs/heptag.jar
CP=${CP}:libs/fastutil-7.0.6.jar
CP=${CP}:libs/jopt-simple-4.9.jar
CP=${CP}:libs/commons-math3-3.5.jar
CP=${CP}:libs/bioc.jar
CP=${CP}:libs/stax-utils.jar
CP=${CP}:libs/stax2-api-3.1.1.jar
CP=${CP}:libs/woodstox-core-asl-4.2.0.jar
CP=${CP}:libs/slf4j-api-1.7.20.jar
CP=${CP}:libs/slf4j-simple-1.7.20.jar
PR="-Dorg.slf4j.simpleLogger.defaultLogLevel=info -Dorg.slf4j.simpleLogger.showThreadName=false -Dorg.slf4j.simpleLogger.showLogName=false -Dorg.slf4j.simpleLogger.logFile=System.out"
REGULARIZATION=$1# format should either be "BioC" or "Pubtator"
FORMAT=$1 
MODEL=$2
INPUT=$3
OUTPUT=$4
AB3P_COMMAND="./identify_abbr"
AB3P_DIR="/home/carsten/Downloads/Ab3P-v1.5"
TEMP="temp"
Ab3P_TIMEOUT=1000
OPT="--inputFilename ${INPUT}"
OPT="${OPT} --fileFormat ${FORMAT}"
OPT="${OPT} --outputFilename ${OUTPUT}"
OPT="${OPT} --modelInputFilename ${MODEL}"
OPT="${OPT} --useSentenceBreaker true"
OPT="${OPT} --abbreviationPostProcessingArgs 1|1|false"
OPT="${OPT} --consistencyPostProcessingArgs 10|1"
OPT="${OPT} --abbreviationSource ncbi.taggerOne.abbreviation.Ab3PAbbreviationSource|${AB3P_COMMAND}|${AB3P_DIR}|${TEMP}|${Ab3P_TIMEOUT}"
echo ${OPT}
java ${PR} -Xmx50G -Xms24G -cp ${CP} ncbi.taggerOne.ProcessText ${OPT}
