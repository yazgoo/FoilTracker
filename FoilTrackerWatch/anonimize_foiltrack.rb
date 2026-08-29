#!/usr/bin/env ruby

require "nokogiri"
require "time"

base_lat=42.42413246240523
base_lon=77.2760976192525

Point = Struct.new(:time, :lat, :lon)

path = ARGV[0]
doc = Nokogiri::XML(File.read(path))

points = doc.xpath("//*[local-name()='trkpt']").map do |p|
  Point.new(
    p.at_xpath("./*[local-name()='time']").text,
    p["lat"].to_f,
    p["lon"].to_f
  )
end

puts <<~EOF
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1"
     creator="FoilTracker"
     xmlns="http://www.topografix.com/GPX/1/1">
    <trk>
        <name>FoilTracker WHS</name>
        <trkseg>
EOF
points.each do |p|
  puts <<~EOF
<trkpt lat="#{p.lat - points.first.lat + base_lat}" lon="#{p.lon - points.first.lon + base_lon}">
    <time>#{p.time}</time>
    <extensions>
        <provider>whs</provider>
    </extensions>
</trkpt>
  EOF
end
puts <<~EOF
        </trkseg>
    </trk>
</gpx>
EOF
