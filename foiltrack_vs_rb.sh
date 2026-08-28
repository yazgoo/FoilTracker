paste -d '|' <(./speed_from_gpx.rb "$1" | column -t) <(./foiltrack_from_gpx.sh "$1" | column -t) | sed 's/|/  |  /'
