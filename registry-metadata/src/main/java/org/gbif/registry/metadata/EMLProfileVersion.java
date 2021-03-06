package org.gbif.registry.metadata;

/**
 * Enum of all GBIF Metadata Profile versions.
 */
public enum EMLProfileVersion {

  GBIF_1_0("1.0", "http://rs.gbif.org/schema/eml-gbif-profile/1.0/eml.xsd"),
  GBIF_1_0_1("1.0.1", "http://rs.gbif.org/schema/eml-gbif-profile/1.0.1/eml.xsd"),
  GBIF_1_0_2("1.0.2", "http://rs.gbif.org/schema/eml-gbif-profile/1.0.2/eml.xsd"),
  GBIF_1_1("1.1", "http://rs.gbif.org/schema/eml-gbif-profile/1.1/eml.xsd");

  private String version;
  private String schemaLocation;

  EMLProfileVersion(String version, String schemaLocation){
    this.version = version;
    this.schemaLocation = schemaLocation;
  }

  /**
   * @return textual representation of the version
   */
  public String getVersion(){
    return version;
  }

  /**
   * @return textual representation of the schema location
   */
  public String getSchemaLocation(){
    return schemaLocation;
  }
}
