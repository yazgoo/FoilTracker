#!/bin/env bash
for f in fixtures/*.gpx
do 
    echo "$f ⏳"
    diff expectations/$(basename "$f").expected <(./foiltrack_vs_rb.sh $PWD/"$f") || exit 1
    echo "$f ✅"
done

