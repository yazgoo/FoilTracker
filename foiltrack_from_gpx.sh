(
./gradlew :core:run --args="'$1'"|grep '=00:20$' -B1|grep -v '=00:20$' 
./gradlew :core:run --args="'$1'"|grep runDuration|tail -1
)| sed 's/.*runDuration=//'|grep -v '00:00$'|grep -v -- --
