#!/usr/bin/env ruby

require "nokogiri"
require "time"

Point = Struct.new(:time, :lat, :lon)
Step = Struct.new(:time, :speed)

def distance(a, b)
  r = 6_371_000.0

  lat1 = a.lat * Math::PI / 180
  lat2 = b.lat * Math::PI / 180
  dlat = (b.lat - a.lat) * Math::PI / 180
  dlon = (b.lon - a.lon) * Math::PI / 180

  x = Math.sin(dlat / 2)**2 +
      Math.cos(lat1) * Math.cos(lat2) * Math.sin(dlon / 2)**2

  2 * r * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x))
end

def sec_to_s(s)
  min, sec = s.to_i.divmod(60)
  if min > 59
    h, min = min.to_i.divmod(60)
    "%02d:%02d:%02d" % [h, min, sec]
  else
    "%02d:%02d" % [min, sec]
  end
end

path = ARGV[0]
offset = (ARGV[1] || 0).to_f
doc = Nokogiri::XML(File.read(path))

extension = path.split(".").last.upcase.to_sym

points = []

if extension == :GPX
  points = doc.xpath("//*[local-name()='trkpt']").map do |p|
    Point.new(
      Time.parse(p.at_xpath("./*[local-name()='time']").text),
      p["lat"].to_f,
      p["lon"].to_f
    )
  end
elsif extension == :TCX
  points = doc.xpath("//*[local-name()='Trackpoint']").map do |p|
    Point.new(
      Time.parse(p.at_xpath("./*[local-name()='Time']").text),
      p.at_xpath(".//*[local-name()='LongitudeDegrees']").text.to_f,
      p.at_xpath(".//*[local-name()='LatitudeDegrees']").text.to_f
    )
  end
else
  puts "unsupported extension #{extension}"
end


start_time = points.first.time
steps = points.each_cons(2).map do |a, b|
  dl = distance(a, b)
  dt = b.time - a.time
  speed = dl / dt * 3.6
  Step.new(b.time - start_time - offset, speed)
end

MIN_SPEED = 10
MAX_SPEED = 30
MAX_TIME_STEP = 6
# MAX_ACCEL = 5
MIN_TIME_S = 20

ranges = steps
  .filter { |s| MIN_SPEED < s.speed and s.speed < MAX_SPEED }
  #.each_cons(2).filter_map { |step, nxt| step if ((nxt.speed - step.speed)/(nxt.time - step.time)).abs < MAX_ACCEL  }
  .chunk_while { |a, b| b.time - a.time <= MAX_TIME_STEP }
  .filter { |x| x.last.time - x.first.time > MIN_TIME_S }

ranges.each { |x| puts "-> #{sec_to_s(x.last.time)}\t#{sec_to_s(x.last.time - x.first.time)}" }

File.open("/tmp/speed.dat", "w") do |f|
  steps.each do |s|
    f.puts "#{s.time} #{s.speed}"
  end
end

# Detected ranges
File.open("/tmp/ranges.dat", "w") do |f|
  ranges.each do |r|
    f.puts "#{r.first.time} #{r.last.time}"
  end
end

File.open("/tmp/speed.gnuplot", "w") do |f|
  f.puts "set title 'GPS speed'"
  f.puts "set xlabel 'Time (s)'"
  f.puts "set ylabel 'Speed (km/h)'"
  f.puts "set grid"
  f.puts "set key top left"
  f.puts "set style fill transparent solid 0.15 noborder"

  ranges.each_with_index do |r, i|
    from = r.first.time
    to   = r.last.time

    f.puts "set object #{i + 1} rect from #{from}, graph 0 to #{to}, graph 1"
  end

  f.puts <<~GNUPLOT
    plot "/tmp/speed.dat" using 1:2 with lines linewidth 2 title "Speed"
    pause mouse close
  GNUPLOT
end
