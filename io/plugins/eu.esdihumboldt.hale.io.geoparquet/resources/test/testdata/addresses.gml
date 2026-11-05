<?xml version="1.0" encoding="UTF-8"?>
<!--
  INSPIRE Addresses (3.0) test data for the schema in the test data of the GML
  plugin (sample_wva/inspire3/Addresses.xsd). The position of an address is
  unbounded, the second address has two positions.
-->
<gml:FeatureCollection gml:id="c1"
    xmlns:gml="http://www.opengis.net/gml/3.2"
    xmlns:ad="urn:x-inspire:specification:gmlas:Addresses:3.0"
    xmlns:base="urn:x-inspire:specification:gmlas:BaseTypes:3.2">
  <gml:featureMember>
    <ad:Address gml:id="a1">
      <ad:inspireId>
        <base:Identifier>
          <base:localId>a1</base:localId>
          <base:namespace>test</base:namespace>
        </base:Identifier>
      </ad:inspireId>
      <ad:position>
        <ad:GeographicPosition>
          <ad:geometry>
            <gml:Point gml:id="p1" srsName="urn:ogc:def:crs:EPSG::25832">
              <gml:pos>500000.0 5500000.0</gml:pos>
            </gml:Point>
          </ad:geometry>
          <ad:default>true</ad:default>
        </ad:GeographicPosition>
      </ad:position>
    </ad:Address>
  </gml:featureMember>
  <gml:featureMember>
    <ad:Address gml:id="a2">
      <ad:inspireId>
        <base:Identifier>
          <base:localId>a2</base:localId>
          <base:namespace>test</base:namespace>
        </base:Identifier>
      </ad:inspireId>
      <ad:position>
        <ad:GeographicPosition>
          <ad:geometry>
            <gml:Point gml:id="p2" srsName="urn:ogc:def:crs:EPSG::25832">
              <gml:pos>501000.0 5501000.0</gml:pos>
            </gml:Point>
          </ad:geometry>
          <ad:default>true</ad:default>
        </ad:GeographicPosition>
      </ad:position>
      <ad:position>
        <ad:GeographicPosition>
          <ad:geometry>
            <gml:Point gml:id="p3" srsName="urn:ogc:def:crs:EPSG::25832">
              <gml:pos>501010.0 5501010.0</gml:pos>
            </gml:Point>
          </ad:geometry>
          <ad:default>false</ad:default>
        </ad:GeographicPosition>
      </ad:position>
    </ad:Address>
  </gml:featureMember>
</gml:FeatureCollection>
