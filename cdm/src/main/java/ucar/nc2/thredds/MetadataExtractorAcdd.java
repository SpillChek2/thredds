package ucar.nc2.thredds;

import thredds.catalog.InvDatasetImpl;
import thredds.catalog.InvDocumentation;
import thredds.catalog.ThreddsMetadata;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.constants.ACDD;
import ucar.nc2.constants.CF;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.units.DateRange;
import ucar.nc2.units.DateType;
import ucar.nc2.units.TimeDuration;

import java.text.ParseException;
import java.util.Map;

/**
 * Extract ACDD metaddata from dataset and promote into the catalog objects
 *
 * @author caron
 * @since 9/14/13
 */
public class MetadataExtractorAcdd {
  private Map<String,Attribute> ncfile;
  private InvDatasetImpl ds;

  public MetadataExtractorAcdd(Map<String,Attribute> ncfile, InvDatasetImpl ds) {
    this.ncfile = ncfile;
    this.ds = ds;
  }

  public void extract() {

    if (ds.getGeospatialCoverage() == null) { // thredds metadata takes precedence
      ds.setGeospatialCoverage( extractGeospatialCoverage());
    }

    Attribute att = ncfile.get(ACDD.keywords);
    if (att != null) {
      String keywordList = att.getStringValue();
      Attribute att2 = ncfile.get(ACDD.keywords_vocabulary);
      String keywords_vocabulary = (att2 == null) ? null : att2.getStringValue();
      addKeywords(keywordList, keywords_vocabulary);
    }

    if (ds.getAuthority() == null) {  // thredds metadata takes precedence
      att = ncfile.get(ACDD.naming_authority);
      if (att != null) {
        ds.setAuthority(att.getStringValue());
      }
    }

    if (ds.getDataType() == null) { // thredds metadata takes precedence
      att = ncfile.get(ACDD.cdm_data_type);
      if (att != null && att.isString()) {
        String val = att.getStringValue();
        FeatureType ft = FeatureType.getType(val);
        if (ft == null) {
          CF.FeatureType cf = CF.FeatureType.getFeatureType(val);
          if (cf != null) ft = CF.FeatureType.convert(cf);
        }
        if (ft != null) ds.setDataType( ft);
      }
    }

    addDocumentation(ACDD.summary);
    addDocumentation(ACDD.history);
    addDocumentation(ACDD.comment);
    addDocumentation(ACDD.processing_level);
    addDocumentation(ACDD.acknowledgement, "funding");
    addDocumentation(ACDD.license, "rights");

    addDate(ACDD.date_created);
    addDate(ACDD.date_modified);

    addSource(true, ACDD.creator, ACDD.creator_url, ACDD.creator_email);
    addSource(false, ACDD.publisher, ACDD.publisher_url, ACDD.publisher_email);

    // swallow
    ds.finish();
  }

  public void extractTimeCoverage() {
    Attribute startTimeAtt = ncfile.get(ACDD.TIME_START);
    Attribute endTimeAtt = ncfile.get(ACDD.TIME_END);
    Attribute durationAtt = ncfile.get(ACDD.TIME_DURATION);
    Attribute resAtt = ncfile.get(ACDD.TIME_RESOLUTION);

    DateType start = null;
    try {
      start = (startTimeAtt == null) ? null : new DateType(startTimeAtt.getStringValue(), null, null);
    } catch (ParseException e) {
      return;
    }

    DateType end = null;
    try {
      end = (endTimeAtt == null) ? null : new DateType(endTimeAtt.getStringValue(), null, null);
    } catch (ParseException e) {
      return;
    }

    TimeDuration duration = null;
    try {
      duration = (durationAtt == null) ? null : new TimeDuration(durationAtt.getStringValue());
    } catch (ParseException e) {
      return;
    }

    TimeDuration resolution = null;
    try {
      resolution = (resAtt == null) ? null : new TimeDuration(resAtt.getStringValue());
    } catch (ParseException e) {
      return;
    }

    try {
      DateRange tc = new DateRange(start, end, duration, resolution);
      ThreddsMetadata tm = ds.getLocalMetadata();
      tm.setTimeCoverage(tc);

    } catch (Exception e) {
      return;
    }

  }



  public ThreddsMetadata.GeospatialCoverage extractGeospatialCoverage() {
    ThreddsMetadata.Range latRange = makeRange( false, ACDD.LAT_MIN, ACDD.LAT_MAX, ACDD.LAT_RESOLUTION, ACDD.LAT_UNITS);
    if (latRange == null) return null;

    ThreddsMetadata.Range lonRange = makeRange( true, ACDD.LON_MIN, ACDD.LON_MAX, ACDD.LON_RESOLUTION, ACDD.LON_UNITS);
    if (lonRange == null) return null;

    ThreddsMetadata.Range altRange = makeRange( false, ACDD.VERT_MIN, ACDD.VERT_MAX, ACDD.VERT_RESOLUTION, ACDD.VERT_UNITS);
    Attribute zposAtt = ncfile.get(ACDD.VERT_IS_POSITIVE);
    String zIsPositive = (zposAtt == null) ? null : zposAtt.getStringValue();

    return new ThreddsMetadata.GeospatialCoverage(lonRange, latRange, altRange, null, zIsPositive);
  }

  private ThreddsMetadata.Range makeRange(boolean isLon, String minName, String maxName, String resName, String unitsName) {
    Attribute minAtt = ncfile.get(minName);
    Attribute maxAtt = ncfile.get(maxName);
    if (minAtt == null || maxAtt == null) return null;

    double min = minAtt.getNumericValue().doubleValue();
    double max = maxAtt.getNumericValue().doubleValue();
    double size = max - min;
    if (isLon && max < min) {
      size += 360;
    }

    Attribute resAtt = ncfile.get(resName);
    double res = (resAtt == null) ? Double.NaN : resAtt.getNumericValue().doubleValue();

    Attribute unitAtt = ncfile.get(unitsName);
    String units = (unitAtt == null) ? null : unitAtt.getStringValue();

    return new ThreddsMetadata.Range(min, size, res, units);
  }

  private void addDocumentation(String docType) {
    Attribute att = ncfile.get(docType);
    if (att != null) {
      String docValue = att.getStringValue();
      String dsValue = ds.getDocumentation(docType);    // metadata/documentation[@type="docType"]
      if (dsValue == null || !dsValue.equals(docValue))
        ds.addDocumentation(new InvDocumentation(null, null, null, docType, docValue));
    }
  }

  private void addDocumentation(String attName, String docType) {
    Attribute att = ncfile.get(attName);
    if (att != null) {
      String docValue = att.getStringValue();
      String dsValue = ds.getDocumentation(docType);        // metadata/documentation[@type="docType"]
      if (dsValue == null || !dsValue.equals(docValue))
        ds.addDocumentation(new InvDocumentation(null, null, null, docType, docValue));
    }
  }

  private void addKeywords(String keywordList, String vocabulary)  {
    String[] keywords = keywordList.split(",");

    ThreddsMetadata tm = ds.getLocalMetadata();
    for (String kw : keywords)
      tm.addKeyword(new ThreddsMetadata.Vocab(kw, vocabulary));
  }

  private void addDate(String dateType) {
    Attribute att = ncfile.get(dateType);
    if (att != null) {
      String dateValue = att.getStringValue();

      ThreddsMetadata tm = ds.getLocalMetadata();
      try {
        tm.addDate(new DateType(dateValue, null, dateType));
      } catch (ParseException e) {
        e.printStackTrace();
      }
    }
  }

  private void addSource(boolean isCreator, String sourceName, String urlName, String emailName) {
    Attribute att = ncfile.get(sourceName);
    if (att != null) {
      String sourceValue = att.getStringValue();

      Attribute urlAtt = ncfile.get(urlName);
      String url = (urlAtt == null) ? null : urlAtt.getStringValue();

      Attribute emailAtt = ncfile.get(emailName);
      String email = (emailAtt == null) ? null : emailAtt.getStringValue();

      ThreddsMetadata.Vocab name = new ThreddsMetadata.Vocab(sourceValue, null);
      ThreddsMetadata.Source src = new ThreddsMetadata.Source(name, url, email);

      ThreddsMetadata tm = ds.getLocalMetadata();
      if (isCreator)
        tm.addCreator(src);
      else
        tm.addPublisher(src);
    }
  }

}
