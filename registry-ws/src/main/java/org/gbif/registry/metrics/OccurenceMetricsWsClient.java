package org.gbif.registry.metrics;

import java.util.UUID;
import javax.ws.rs.core.MultivaluedMap;

import com.google.inject.Inject;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 *
 * HTTP based WebService client for Occurrence metrics.
 * This class uses the HTTP API and includes no dependencies of the metrics project.
 *
 */
public class OccurenceMetricsWsClient implements OccurrenceMetricsClient {

  public static final String DATASET_KEY = "datasetKey";
  private static final String OCCURRENCE_COUNT_PATH = "occurrence/count";
  private WebResource resource;

  /**
   *
   * @param resource WebResource pointing to the API
   */
  @Inject
  public OccurenceMetricsWsClient(WebResource resource) {
    this.resource = resource.path(OCCURRENCE_COUNT_PATH);
  }

  @Override
  public Long getCountForDataset(UUID datasetKey) {
    MultivaluedMap<String, String> params = new MultivaluedMapImpl();
    params.putSingle(DATASET_KEY, datasetKey.toString());
    return resource.queryParams(params).get(Long.class);
  }
}
