package org.gbif.registry.cli.doisynchronizer;

import org.gbif.api.model.common.DOI;
import org.gbif.api.model.common.DoiData;
import org.gbif.api.model.common.DoiStatus;
import org.gbif.api.model.common.User;
import org.gbif.api.model.occurrence.Download;
import org.gbif.api.model.registry.Dataset;
import org.gbif.api.model.registry.Identifier;
import org.gbif.api.service.common.UserService;
import org.gbif.api.vocabulary.IdentifierType;
import org.gbif.doi.service.DoiException;
import org.gbif.doi.service.datacite.DataCiteService;
import org.gbif.registry.cli.common.CommonBuilder;
import org.gbif.registry.cli.doisynchronizer.diagnostic.DoiDiagnosticPrinter;
import org.gbif.registry.cli.doisynchronizer.diagnostic.GbifDOIDiagnosticResult;
import org.gbif.registry.cli.doisynchronizer.diagnostic.GbifDatasetDOIDiagnosticResult;
import org.gbif.registry.cli.doisynchronizer.diagnostic.GbifDownloadDOIDiagnosticResult;
import org.gbif.registry.doi.DoiPersistenceService;
import org.gbif.registry.doi.DoiType;
import org.gbif.registry.doi.handler.DataCiteDoiHandlerStrategy;
import org.gbif.registry.persistence.mapper.DatasetMapper;
import org.gbif.registry.persistence.mapper.OccurrenceDownloadMapper;

import java.util.List;
import java.util.Map;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Preconditions;
import com.google.inject.Injector;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This service allows to print a report of DOI and/or try to fix them by synchronizing with Datacite.
 * This service is mainly design to be run manually and uses System.out.
 */
public class DoiSynchronizerService {

  private static final Logger LOG = LoggerFactory.getLogger(DoiSynchronizerService.class);

  private final DoiSynchronizerConfiguration config;

  private final DoiPersistenceService doiPersistenceService;
  private final DataCiteDoiHandlerStrategy dataCiteDoiHandlerStrategy;

  private final DatasetMapper datasetMapper;
  private final OccurrenceDownloadMapper downloadMapper;
  private final UserService userService;
  private final DataCiteService dataCiteService;

  private DoiDiagnosticPrinter diagnosticPrinter = new DoiDiagnosticPrinter(System.out);

  public DoiSynchronizerService(DoiSynchronizerConfiguration config) {
    this(config, new DoiSynchronizerModule(config).getInjector());
  }

  public DoiSynchronizerService(DoiSynchronizerConfiguration config, Injector injector) {
    this.config = config;
    doiPersistenceService = injector.getInstance(DoiPersistenceService.class);
    dataCiteDoiHandlerStrategy = injector.getInstance(DataCiteDoiHandlerStrategy.class);
    datasetMapper = injector.getInstance(DatasetMapper.class);
    downloadMapper = injector.getInstance(OccurrenceDownloadMapper.class);
    userService = injector.getInstance(UserService.class);

    dataCiteService = CommonBuilder.createDataCiteService(config.datacite);
  }

  /**
   * Runs the actual service
   */
  public void doRun(){
    if (StringUtils.isNotBlank(config.doi)) {
      try {
        DOI doi = new DOI(config.doi);
        reportDOIStatus(doi);

        if (config.fixDOI) {
          System.out.println("Attempt to fix DOI " + doi.getDoiName());
          System.out.println("Attempt result: " + (tryFixDOI(doi) ? "success" : "failed"));
        }
      } catch (IllegalArgumentException iaEx) {
        System.out.println(config.doi + " is not a valid DOI");
      }
    }
  }

  /**
   * Report the current status of a DOI
   * @param doi
   */
  private void reportDOIStatus(DOI doi){
    GbifDOIDiagnosticResult doiDiagnostic = generateGbifDOIDiagnostic(doi);

    if(doiDiagnostic != null){
      diagnosticPrinter.printReport(doiDiagnostic);
    }
    else{
      System.out.println("No report can be generated. Nothing found for DOI " + doi);
    }
  }

  /**
   * Try to fix a DOI if possible
   * @param doi
   * @return
   */
  private boolean tryFixDOI(DOI doi){
    DoiType doiType = doiPersistenceService.getType(doi);
    if(doiType == null){
      return false;
    }

    switch (doiType){
      case DATASET: return reapplyDatasetDOIStrategy(doi);
      case DOWNLOAD: return reapplyDownloadDOIStrategy(doi);
    }
    return false;
  }

  /**
   * Re-apply the DataCite DOI handling strategy if possible.
   * @param doi
   * @return DataCite DOI handling strategy applied?
   */
  private boolean reapplyDatasetDOIStrategy(DOI doi){
    Preconditions.checkNotNull(doi, "DOI can't be null");

    List<Dataset> datasets = datasetMapper.listByIdentifier(IdentifierType.DOI, doi.toString(), null);
    if(datasets.isEmpty()){
      return false;
    }

    //only try if we have only 1 Dataset for the DOI
    if(datasets.size() == 1){
      Dataset dataset = datasets.iterator().next();
      DOI datasetDoi = dataset.getDoi();
      if(doi.equals(datasetDoi)) {
        dataCiteDoiHandlerStrategy.datasetChanged(dataset, null);
        return true;
      }
      //DOI changed
      else {
        // The dataset DOI is not issued by GBIF
        if(!dataCiteDoiHandlerStrategy.isUsingMyPrefix(datasetDoi)){
          boolean doiIsInAlternateIdentifiers = isIdentifierDOIFound(doi, dataset);
          boolean datasetDoiIsInAlternateIdentifiers = isIdentifierDOIFound(datasetDoi, dataset);
          // check we are in a known state which means:
          // - The current dataset DOI is not in alternative identifiers but the previous GBIF DOI is
          // the logic is applied by the registry
          if(doiIsInAlternateIdentifiers && !datasetDoiIsInAlternateIdentifiers){
            dataCiteDoiHandlerStrategy.datasetChanged(dataset, doi);
            return true;
          }
        }
        else{
          LOG.error("Can not handle cases where the DOI changed but the list of alternate identifiers is not updated");
        }
      }
    }

    return false;
  }

  /**
   * Checks if a DOI can be found in the list of dataset identifiers.
   *
   * @param doi
   * @param dataset
   * @return
   */
  private boolean isIdentifierDOIFound(DOI doi, Dataset dataset){
    List<Identifier> identifiers = dataset.getIdentifiers();
    for(Identifier currentIdentifier : identifiers){
      if(IdentifierType.DOI.equals(currentIdentifier.getType())){
        if(currentIdentifier.getIdentifier().equals(doi.toString())){
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Re-apply the Download DOI strategy from dataCiteDoiHandlerStrategy.
   * @param doi
   * @return success or not
   */
  private boolean reapplyDownloadDOIStrategy(DOI doi){

    Preconditions.checkNotNull(doi, "DOI can't be null");

    Download download = downloadMapper.getByDOI(doi);
    if(download == null){
      return false;
    }

    // only handle download with status SUCCEEDED
    if(!Download.Status.SUCCEEDED.equals(download.getStatus())){
      LOG.error("Download with DOI {} status is {}", doi, download.getStatus());
      return false;
    }

    //retrieve User
    String creatorName = download.getRequest().getCreator();
    User user = userService.get(creatorName);

    if(user == null){
      LOG.error("No user with creator name {} can be found", creatorName);
      return false;
    }

    dataCiteDoiHandlerStrategy.downloadChanged(download, null, user);
    return true;
  }

  /**
   * Compare the metadata linked to a DOI between what we have in the database and what is stored at Datacite.
   *
   * @param doi
   * @return
   */
  private boolean compareMetadataWithDatacite(DOI doi){
    String registryDoiMetadata = doiPersistenceService.getMetadata(doi);
    String dataciteDoiMetadata = null;
    try {
      dataciteDoiMetadata = dataCiteService.getMetadata(doi);
    } catch (DoiException e) {
      LOG.error("Can't compare DOI metadata", e);
    }
    return StringUtils.equals(registryDoiMetadata, dataciteDoiMetadata);
  }

  /**
   * Try to fix a DOI based on the {@link GbifDOIDiagnosticResult}.
   *
   *
   *
   * @param result
   */
  private void fixFailedStatusDoi(GbifDOIDiagnosticResult result){

//    if(result.getRelatedDataset() != null){
//      if(result.isCurrentDOI()){
//        dataCiteDoiHandlerStrategy.datasetChanged(result.getRelatedDataset(), null);
//        System.out.println("datasetChanged triggered on dataCiteDoiHandlerStrategy");
//      }
//      else{
//        System.out.println("not implemented yet");
//
//      }
//      if(!result.isCurrentDOI() && !result.getRelatedDataset().getDoi().getPrefix().equals(DOI.GBIF_PREFIX)){
//        dataCiteDoiHandlerStrategy.datasetChanged(result.getRelatedDataset(), result.getDoi());
//        //dataciteMetadata = buildMetadataForNonGbifDOI(result.getRelatedDataset());
//      }
//      else{
//        dataciteMetadata = buildMetadata(result.getRelatedDataset());
//        dataCiteDoiHandlerStrategy.
//      }
    // scheduleRegistration(result.getDoi(), dataciteMetadata, result.getRelatedDataset().getKey());
    // System.out.println("Scheduled Registration for DOI " + result.getDoi().getDoiName());
//    }
  }

  /**
   * Get the list of DOIGbifDataciteDiagnostic for a specific DoiType.
   *
   * @param doiType
   *
   */
  private List<GbifDOIDiagnosticResult> runDOIStatusDiagnostic(DoiType doiType){
    List<GbifDOIDiagnosticResult> list = Lists.newArrayList();
    //get all the DOI with the FAILED status. Note that they are all GBIF assigned DOI.
    List<Map<String,Object>> failedDoiList = doiPersistenceService.list(DoiStatus.FAILED, doiType, null);
    DOI doi;
    for(Map<String,Object> failedDoi : failedDoiList ) {
      doi = new DOI((String) failedDoi.get("doi"));
      list.add(generateGbifDOIDiagnostic(doi));
    }
    return list;
  }

  /**
   * Check the status of a DOI between GBIF and Datacite.
   *
   * @param doi
   * @return
   */
  private GbifDOIDiagnosticResult generateGbifDOIDiagnostic(DOI doi) {
    GbifDOIDiagnosticResult doiGbifDataciteDiagnostic = null;

    DoiType doiType = doiPersistenceService.getType(doi);
    if(doiType != null) {
      switch (doiType) {
        case DATASET:
          doiGbifDataciteDiagnostic = createGbifDOIDatasetDiagnostic(doi);
          break;
        case DOWNLOAD:
          doiGbifDataciteDiagnostic = createGbifDOIDownloadDiagnostic(doi);
          break;
        default:
      }
    }

    if(doiGbifDataciteDiagnostic == null){
      return null;
    }

    DoiData doiData = doiPersistenceService.get(doi);
    doiGbifDataciteDiagnostic.setDoiData(doiData);

    try {
      doiGbifDataciteDiagnostic.setDoiExistsAtDatacite(dataCiteService.exists(doi));
    } catch (DoiException e) {
      LOG.warn("Can not check existence of DOI " + doi.getDoiName(), e);
    }

    if(doiGbifDataciteDiagnostic.isDoiExistsAtDatacite()) {
      doiGbifDataciteDiagnostic.setMetadataEquals(compareMetadataWithDatacite(doi));

      try {
        DoiData doiStatus = dataCiteService.resolve(doi);
        doiGbifDataciteDiagnostic.setDataciteDoiStatus(doiStatus.getStatus());
        doiGbifDataciteDiagnostic.setDataciteTarget(doiStatus.getTarget());
      } catch (DoiException e) {
        LOG.error("Failed to resolve DOI {}", doi);
      }
    }
    return doiGbifDataciteDiagnostic;
  }

  private GbifDOIDiagnosticResult createGbifDOIDatasetDiagnostic(DOI doi) {
    GbifDatasetDOIDiagnosticResult datasetDiagnosticResult = new GbifDatasetDOIDiagnosticResult(doi);

    List<Dataset> datasets = datasetMapper.listByIdentifier(IdentifierType.DOI, doi.toString(), null);
    //Could they conflict??
    if(!datasets.isEmpty()) {
      datasetDiagnosticResult.setRelatedDataset(datasets);
    }
    else{
      datasetDiagnosticResult.setRelatedDataset(datasetMapper.listByDOI(doi.getDoiName()));
    }

    if(datasetDiagnosticResult.isLinkedToASingleDataset()){
      datasetDiagnosticResult.setDoiIsInAlternateIdentifiers(isIdentifierDOIFound(doi, datasetDiagnosticResult.getRelatedDataset()));
    }

    return datasetDiagnosticResult;
  }

  private GbifDOIDiagnosticResult createGbifDOIDownloadDiagnostic(DOI doi) {
    GbifDownloadDOIDiagnosticResult downloadDiagnosticResult = new GbifDownloadDOIDiagnosticResult(doi);

    Download download = downloadMapper.getByDOI(doi);
    downloadDiagnosticResult.setDownload(download);

    return downloadDiagnosticResult;
  }


}