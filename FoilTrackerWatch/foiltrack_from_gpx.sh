(
./gradlew :core:build :core:run --args="'$1'"|awk -F'runDuration=' '{
    if ($2 == prev) {
        count++
    } else {
        prev = $2
        count = 1
        first = $0
    }

    if (count == 4)
        print first
}'
)| sed -E 's/.*reltime=([^ ]*) .*runDuration=(.*)$/-> \1  \2/'|grep -v '00:00$'|grep -v -- --|grep -vE '^$'
