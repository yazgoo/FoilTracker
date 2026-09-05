# $1 must be of the form fixtures/foiltrack_20260816_193743.gpx
./foiltrack_vs_rb.sh "$PWD"/"$1"  > expectations/$(basename "$1").expected
