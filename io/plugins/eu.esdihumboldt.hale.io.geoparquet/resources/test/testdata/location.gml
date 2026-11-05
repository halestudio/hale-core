<?xml version="1.0" encoding="UTF-8"?>
<!-- Places located with gml:location and gml:priorityLocation (see location-gml32.xsd). -->
<gml:FeatureCollection gml:id="c1" xmlns:gml="http://www.opengis.net/gml/3.2"
    xmlns:t="http://www.example.com/location">
  <gml:featureMember>
    <t:Place gml:id="p1">
      <gml:location>
        <gml:Point gml:id="g1" srsName="urn:ogc:def:crs:EPSG::25832">
          <gml:pos>500000.0 5500000.0</gml:pos>
        </gml:Point>
      </gml:location>
      <t:label>location</t:label>
    </t:Place>
  </gml:featureMember>
  <gml:featureMember>
    <t:Place gml:id="p2">
      <gml:priorityLocation>
        <gml:Point gml:id="g2" srsName="urn:ogc:def:crs:EPSG::25832">
          <gml:pos>501000.0 5501000.0</gml:pos>
        </gml:Point>
      </gml:priorityLocation>
      <t:label>priorityLocation</t:label>
    </t:Place>
  </gml:featureMember>
</gml:FeatureCollection>
