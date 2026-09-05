#!/bin/env bash
for f in fixtures/*.gpx
do 
    echo "$f ⏳"
    diff <(./foiltrack_vs_rb.sh $PWD/"$f") expectations/$(basename "$f").expected || exit 1
    echo "$f ✅"
done

