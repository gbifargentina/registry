package org.gbif.registry.collections;

import org.gbif.api.model.collections.CollectionEntity;
import org.gbif.api.model.common.paging.Pageable;
import org.gbif.api.service.collections.CrudService;
import org.gbif.registry.database.DatabaseInitializer;
import org.gbif.registry.database.LiquibaseInitializer;
import org.gbif.registry.database.LiquibaseModules;
import org.gbif.registry.grizzly.RegistryServer;
import org.gbif.registry.ws.fixtures.TestConstants;
import org.gbif.ws.client.filter.SimplePrincipalProvider;

import java.util.UUID;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.validation.ValidationException;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Base class to test the CRUD operations of {@link CollectionEntity}. */
public abstract class CrudTest<T extends CollectionEntity> {

  protected static final BiFunction<Integer, Long, Pageable> PAGE =
      (limit, offset) ->
          new Pageable() {
            @Override
            public int getLimit() {
              return limit;
            }

            @Override
            public long getOffset() {
              return offset;
            }
          };

  private final CrudService<T> crudService;
  private final SimplePrincipalProvider pp;

  @ClassRule
  public static final LiquibaseInitializer liquibaseRule =
      new LiquibaseInitializer(LiquibaseModules.database());

  @ClassRule public static final RegistryServer registryServer = RegistryServer.INSTANCE;

  @Rule
  public final DatabaseInitializer databaseRule =
      new DatabaseInitializer(LiquibaseModules.database());

  public CrudTest(CrudService<T> crudService, @Nullable SimplePrincipalProvider pp) {
    this.crudService = crudService;
    this.pp = pp;
  }

  @Before
  public void setup() {
    if (pp != null) {
      pp.setPrincipal(TestConstants.TEST_ADMIN);
    }
  }

  protected abstract T newEntity();

  protected abstract void assertNewEntity(T entity);

  protected abstract T updateEntity(T entity);

  protected abstract void assertUpdatedEntity(T entity);

  protected abstract T newInvalidEntity();

  @Test
  public void crudTest() {
    // create
    T entity = newEntity();

    UUID key = crudService.create(entity);
    assertNotNull(key);
    T entitySaved = crudService.get(key);
    assertEquals(key, entitySaved.getKey());
    assertNewEntity(entitySaved);
    assertNotNull(entitySaved.getCreatedBy());
    assertNotNull(entitySaved.getCreated());
    assertNotNull(entitySaved.getModifiedBy());
    assertNotNull(entitySaved.getModified());

    // update
    entity = updateEntity(entitySaved);
    crudService.update(entity);
    entitySaved = crudService.get(key);
    assertUpdatedEntity(entitySaved);

    // delete
    crudService.delete(key);
    entitySaved = crudService.get(key);
    assertNotNull(entitySaved.getDeleted());
  }

  @Test(expected = ValidationException.class)
  public void createInvalidEntityTest() {
    crudService.create(newInvalidEntity());
  }

  @Test
  public void deleteMissingEntityTest() {
    // does nothing
    crudService.delete(UUID.randomUUID());
  }

  @Test(expected = IllegalArgumentException.class)
  public void updateDeletedEntityTest() {
    T entity = newEntity();
    UUID key = crudService.create(entity);
    entity.setKey(key);
    crudService.delete(key);
    crudService.update(entity);
  }

  @Test(expected = ValidationException.class)
  public void updateInvalidEntityTest() {
    T entity = newEntity();
    UUID key = crudService.create(entity);
    entity = newInvalidEntity();
    entity.setKey(key);
    crudService.update(entity);
  }

  @Test
  public void getMissingEntity() {
    assertNull(crudService.get(UUID.randomUUID()));
  }
}
