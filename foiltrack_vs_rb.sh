paste -d '|' <(./speed_from_gpx.rb "$1") <(./foiltrack_from_gpx.sh "$1")
