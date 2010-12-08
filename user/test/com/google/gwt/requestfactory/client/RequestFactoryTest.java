/*
 * Copyright 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.requestfactory.client;

import com.google.gwt.requestfactory.shared.EntityProxy;
import com.google.gwt.requestfactory.shared.EntityProxyChange;
import com.google.gwt.requestfactory.shared.EntityProxyId;
import com.google.gwt.requestfactory.shared.Receiver;
import com.google.gwt.requestfactory.shared.Request;
import com.google.gwt.requestfactory.shared.RequestContext;
import com.google.gwt.requestfactory.shared.ServerFailure;
import com.google.gwt.requestfactory.shared.SimpleBarProxy;
import com.google.gwt.requestfactory.shared.SimpleBarRequest;
import com.google.gwt.requestfactory.shared.SimpleEnum;
import com.google.gwt.requestfactory.shared.SimpleFooProxy;
import com.google.gwt.requestfactory.shared.SimpleFooRequest;
import com.google.gwt.requestfactory.shared.SimpleValueProxy;
import com.google.gwt.requestfactory.shared.Violation;
import com.google.gwt.requestfactory.shared.impl.SimpleEntityProxyId;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link com.google.gwt.requestfactory.shared.RequestFactory}.
 */
public class RequestFactoryTest extends RequestFactoryTestBase {
  /*
   * DO NOT USE finishTest(). Instead, call finishTestAndReset();
   * 
   * When possible, pass any returned proxies to checkSerialization() and use
   * the return value in the place of the returned object.
   */

  class FooReciever extends Receiver<SimpleFooProxy> {
    private SimpleFooProxy mutableFoo;
    private Request<SimpleFooProxy> persistRequest;
    private String expectedException;

    public FooReciever(SimpleFooProxy mutableFoo,
        Request<SimpleFooProxy> persistRequest, String exception) {
      this.mutableFoo = mutableFoo;
      this.persistRequest = persistRequest;
      this.expectedException = exception;
    }

    @Override
    public void onFailure(ServerFailure error) {
      assertEquals(expectedException, error.getExceptionType());
      if (expectedException != null) {
        assertFalse(error.getStackTraceString().length() == 0);
        assertEquals("THIS EXCEPTION IS EXPECTED BY A TEST", error.getMessage());
      } else {
        assertEquals(null, error.getStackTraceString());
        assertEquals("Server Error: THIS EXCEPTION IS EXPECTED BY A TEST",
            error.getMessage());
      }

      // Now show that we can fix the error and try again with the same
      // request

      mutableFoo.setPleaseCrash(24); // Only 42 and 43 crash
      persistRequest.fire(new Receiver<SimpleFooProxy>() {
        @Override
        public void onSuccess(SimpleFooProxy response) {
          response = checkSerialization(response);
          finishTestAndReset();
        }
      });
    }

    @Override
    public void onSuccess(SimpleFooProxy response) {
      fail("Failure expected but onSuccess() was called");
    }

    @Override
    public void onViolation(Set<Violation> errors) {
      fail("Failure expected but onViolation() was called");
    }
  }

  class NullReceiver extends Receiver<Object> {
    @Override
    public void onSuccess(Object response) {
      assertNull(response);
      finishTestAndReset();
    }
  }

  private class FailFixAndRefire<T> extends Receiver<T> {

    private final SimpleFooProxy proxy;
    private final Request<T> request;
    private boolean voidReturnExpected;

    FailFixAndRefire(SimpleFooProxy proxy, RequestContext context,
        Request<T> request) {
      this.proxy = context.edit(proxy);
      this.request = request;
    }

    @Override
    public void onSuccess(T response) {
      /*
       * Make sure your class path includes:
       * 
       * tools/lib/apache/log4j/log4j-1.2.16.jar
       * tools/lib/hibernate/validator/hibernate-validator-4.1.0.Final.jar
       * tools/lib/slf4j/slf4j-api/slf4j-api-1.6.1.jar
       * tools/lib/slf4j/slf4j-log4j12/slf4j-log4j12-1.6.1.jar
       */
      fail("Violations expected (you might be missing some jars, "
          + "see the comment above this line)");
    }

    @Override
    public void onViolation(Set<Violation> errors) {

      // size violation expected

      assertEquals(1, errors.size());
      Violation error = errors.iterator().next();
      assertEquals("userName", error.getPath());
      assertEquals("size must be between 3 and 30", error.getMessage());
      assertEquals(proxy.stableId(), error.getProxyId());

      // Now re-used the request to fix the edit

      proxy.setUserName("long enough");
      request.fire(new Receiver<T>() {
        @Override
        public void onSuccess(T response) {
          if (voidReturnExpected) {
            assertNull(response);
          } else {
            assertEquals(proxy.stableId(),
                ((SimpleFooProxy) response).stableId());
          }
          finishTestAndReset();
        }
      });
    }

    void doTest() {
      proxy.setUserName("a"); // too short
      request.fire(this);
    }

    void doVoidTest() {
      voidReturnExpected = true;
      doTest();
    }
  }

  private static final int DELAY_TEST_FINISH = 10 * 1000;

  public <T extends EntityProxy> void assertContains(Collection<T> col, T value) {
    EntityProxyId<?> lookFor = value.stableId();
    for (T x : col) {
      EntityProxyId<?> found = x.stableId();
      if (found.equals(lookFor)) {
        return;
      }
    }
    assertTrue("Value " + value + " not found in collection " + col.toString(),
        false);
  }

  public <T extends EntityProxy> void assertNotContains(Collection<T> col,
      T value) {
    for (T x : col) {
      assertNotSame(x.stableId(), value.stableId());
    }
  }

  /**
   * Test that removing a parent entity and implicitly removing the child sends
   * an event to the client that the child was removed.
   * 
   * TODO(rjrjr): Should cascading deletes be detected?
   */
  public void disableTestMethodWithSideEffectDeleteChild() {
    delayTestFinish(DELAY_TEST_FINISH);

    // Persist bar.
    SimpleBarRequest context = req.simpleBarRequest();
    final SimpleBarProxy bar = context.create(SimpleBarProxy.class);
    context.persistAndReturnSelf().using(bar).fire(
        new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy persistentBar) {
            persistentBar = checkSerialization(persistentBar);
            // Persist foo with bar as a child.
            SimpleFooRequest context = req.simpleFooRequest();
            SimpleFooProxy foo = context.create(SimpleFooProxy.class);
            final Request<SimpleFooProxy> persistRequest = context.persistAndReturnSelf().using(
                foo);
            foo = context.edit(foo);
            foo.setUserName("John");
            foo.setBarField(bar);
            persistRequest.fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy persistentFoo) {
                persistentFoo = checkSerialization(persistentFoo);
                // Handle changes to SimpleFooProxy.
                final SimpleFooEventHandler<SimpleFooProxy> fooHandler = new SimpleFooEventHandler<SimpleFooProxy>();
                EntityProxyChange.registerForProxyType(req.getEventBus(),
                    SimpleFooProxy.class, fooHandler);

                // Handle changes to SimpleBarProxy.
                final SimpleFooEventHandler<SimpleBarProxy> barHandler = new SimpleFooEventHandler<SimpleBarProxy>();
                EntityProxyChange.registerForProxyType(req.getEventBus(),
                    SimpleBarProxy.class, barHandler);

                // Delete bar.
                SimpleFooRequest context = req.simpleFooRequest();
                final Request<Void> deleteRequest = context.deleteBar().using(
                    persistentFoo);
                SimpleFooProxy editable = context.edit(persistentFoo);
                editable.setBarField(bar);
                deleteRequest.fire(new Receiver<Void>() {
                  @Override
                  public void onSuccess(Void response) {
                    assertEquals(1, fooHandler.updateEventCount); // set bar to
                    // null
                    assertEquals(1, fooHandler.totalEventCount);

                    assertEquals(1, barHandler.deleteEventCount); // deleted bar
                    assertEquals(1, barHandler.totalEventCount);
                    finishTestAndReset();
                  }
                });
              }
            });
          }
        });
  }

  @Override
  public String getModuleName() {
    return "com.google.gwt.requestfactory.RequestFactorySuite";
  }

  /**
   * Test that the same object, referenced twice, points to the same instance.
   */
  public void testAntiAliasing() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().fetchDoubleReference().with("fooField",
        "selfOneToManyField").fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        assertNotNull(response.getFooField());
        assertSame(response.getFooField(),
            response.getSelfOneToManyField().get(0));
        finishTestAndReset();
      }
    });
  }

  /**
   * Test that we can commit child objects.
   */
  public void testCascadingCommit() {
    delayTestFinish(DELAY_TEST_FINISH);
    SimpleFooRequest context = req.simpleFooRequest();
    final SimpleFooProxy foo = context.create(SimpleFooProxy.class);
    final SimpleBarProxy bar0 = context.create(SimpleBarProxy.class);
    final SimpleBarProxy bar1 = context.create(SimpleBarProxy.class);
    List<SimpleBarProxy> bars = new ArrayList<SimpleBarProxy>();
    bars.add(bar0);
    bars.add(bar1);

    final SimpleFooEventHandler<SimpleBarProxy> handler = new SimpleFooEventHandler<SimpleBarProxy>();
    EntityProxyChange.registerForProxyType(req.getEventBus(),
        SimpleBarProxy.class, handler);

    Request<SimpleFooProxy> request = context.persistCascadingAndReturnSelf().using(
        foo);
    SimpleFooProxy editFoo = context.edit(foo);
    editFoo.setOneToManyField(bars);
    request.fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        assertFalse(((SimpleEntityProxyId<SimpleFooProxy>) response.stableId()).isEphemeral());
        assertEquals(2, handler.persistEventCount); // two bars persisted.
        assertEquals(2, handler.updateEventCount); // two bars persisted.
        assertEquals(4, handler.totalEventCount);
        finishTestAndReset();
      }
    });
  }

  public void testChangedCreate() {
    SimpleFooRequest context = simpleFooRequest();

    // Creates don't cause a change
    SimpleFooProxy foo = context.create(SimpleFooProxy.class);
    assertFalse(context.isChanged());

    // Change
    foo.setCharField('c');
    assertTrue(context.isChanged());

    // Undo the change
    foo.setCharField(null);
    assertFalse(context.isChanged());
  }

  public void testChangedEdit() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(1L).fire(
        new Receiver<SimpleFooProxy>() {

          @Override
          public void onSuccess(SimpleFooProxy foo) {
            foo = checkSerialization(foo);
            SimpleFooRequest context = simpleFooRequest();

            // edit() doesn't cause a change
            foo = context.edit(foo);
            assertFalse(context.isChanged());

            final String newName = "something else;";
            String oldName = foo.getUserName();
            assertFalse("Don't accidentally set the same name",
                newName.equals(oldName));

            // gets don't cause a change
            assertFalse(context.isChanged());

            // Change
            foo.setUserName(newName);
            assertTrue(context.isChanged());

            // Undo the change
            foo.setUserName(oldName);
            assertFalse(context.isChanged());

            finishTestAndReset();
          }
        });
  }

  public void testChangedNothing() {
    SimpleFooRequest context = simpleFooRequest();
    assertFalse(context.isChanged());
  }

  public void testClassToken() {
    String token = req.getHistoryToken(SimpleFooProxy.class);
    assertEquals(SimpleFooProxy.class, req.getProxyClass(token));

    SimpleFooProxy foo = simpleFooRequest().create(SimpleFooProxy.class);
    assertEquals(SimpleFooProxy.class, foo.stableId().getProxyClass());
  }

  public void testCollectionSubProperties() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().getSimpleFooWithSubPropertyCollection().with(
        "selfOneToManyField", "selfOneToManyField.fooField").fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            response = checkSerialization(response);
            assertEquals(
                "I'm here",
                response.getSelfOneToManyField().get(0).getFooField().getUserName());
            finishTestAndReset();
          }
        });
  }

  public void testDomainUpcast() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().returnSimpleFooSubclass().fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            response = checkSerialization(response);
            assertEquals(42, response.getIntId().intValue());
            finishTestAndReset();
          }
        });
  }

  public void testDummyCreate() {
    delayTestFinish(DELAY_TEST_FINISH);

    final SimpleFooEventHandler<SimpleFooProxy> handler = new SimpleFooEventHandler<SimpleFooProxy>();
    EntityProxyChange.registerForProxyType(req.getEventBus(),
        SimpleFooProxy.class, handler);

    SimpleFooRequest context = simpleFooRequest();
    final SimpleFooProxy foo = context.create(SimpleFooProxy.class);
    final EntityProxyId<SimpleFooProxy> futureId = foo.stableId();
    assertTrue(((SimpleEntityProxyId<?>) futureId).isEphemeral());
    Request<SimpleFooProxy> fooReq = context.persistAndReturnSelf().using(foo);
    fooReq.fire(new Receiver<SimpleFooProxy>() {

      @Override
      public void onSuccess(SimpleFooProxy returned) {
        returned = checkSerialization(returned);
        EntityProxyId<SimpleFooProxy> returnedId = returned.stableId();
        assertEquals(futureId, returnedId);
        assertFalse((((SimpleEntityProxyId<?>) returnedId).isEphemeral()));
        assertEquals(1, handler.persistEventCount);
        assertEquals(1, handler.updateEventCount);
        assertEquals(2, handler.totalEventCount);

        checkStableIdEquals(foo, returned);
        finishTestAndReset();
      }
    });
  }

  public void testDummyCreateBar() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleBarRequest context = simpleBarRequest();
    final SimpleBarProxy foo = context.create(SimpleBarProxy.class);
    assertTrue(((SimpleEntityProxyId<?>) foo.stableId()).isEphemeral());
    Request<SimpleBarProxy> fooReq = context.persistAndReturnSelf().using(foo);
    fooReq.fire(new Receiver<SimpleBarProxy>() {

      @Override
      public void onSuccess(SimpleBarProxy returned) {
        returned = checkSerialization(returned);
        assertFalse(((SimpleEntityProxyId<?>) foo.stableId()).isEphemeral());

        checkStableIdEquals(foo, returned);
        finishTestAndReset();
      }
    });
  }

  public void testDummyCreateList() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleBarRequest context = simpleBarRequest();
    final SimpleBarProxy bar = context.create(SimpleBarProxy.class);
    assertTrue(((SimpleEntityProxyId<?>) bar.stableId()).isEphemeral());
    Request<SimpleBarProxy> fooReq = context.returnFirst(Collections.singletonList(bar));
    fooReq.fire(new Receiver<SimpleBarProxy>() {

      @Override
      public void onSuccess(SimpleBarProxy returned) {
        returned = checkSerialization(returned);
        assertFalse(((SimpleEntityProxyId<?>) bar.stableId()).isEphemeral());
        assertFalse(((SimpleEntityProxyId<?>) returned.stableId()).isEphemeral());

        checkStableIdEquals(bar, returned);
        finishTestAndReset();
      }
    });
  }

  /**
   * Tests behaviors relating to editing an object with one context and then
   * using with another.
   */
  public void testEditAcrossContexts() {
    SimpleFooRequest contextA = simpleFooRequest();
    final SimpleFooRequest contextB = simpleFooRequest();

    SimpleFooProxy fromA = contextA.create(SimpleFooProxy.class);

    try {
      contextB.edit(fromA);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      contextB.persistAndReturnSelf().using(fromA).fire(
          new Receiver<SimpleFooProxy>() {
            @Override
            public void onSuccess(SimpleFooProxy response) {
              fail();
            }
          });
      fail();
    } catch (IllegalArgumentException expected) {
    }

    delayTestFinish(DELAY_TEST_FINISH);
    contextA.findSimpleFooById(999L).fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        // The response shouldn't be associated with a RequestContext
        contextB.edit(response);
        finishTestAndReset();
      }
    });
  }

  /**
   * Check default value, a newly-set value, and a null value.
   */
  public void testEnumProperty() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            assertEquals(SimpleEnum.FOO, response.getEnumField());
            SimpleFooRequest ctx = simpleFooRequest();
            response = ctx.edit(response);
            response.setEnumField(SimpleEnum.BAR);
            ctx.persistAndReturnSelf().using(response).fire(
                new Receiver<SimpleFooProxy>() {
                  @Override
                  public void onSuccess(SimpleFooProxy response) {
                    assertEquals(SimpleEnum.BAR, response.getEnumField());
                    SimpleFooRequest ctx = simpleFooRequest();
                    response = ctx.edit(response);
                    response.setEnumField(null);
                    ctx.persistAndReturnSelf().using(response).fire(
                        new Receiver<SimpleFooProxy>() {
                          @Override
                          public void onSuccess(SimpleFooProxy response) {
                            assertNull(response.getEnumField());
                            finishTestAndReset();
                          }
                        });
                  }
                });
          }
        });
  }

  public void testFetchEntity() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            response = checkSerialization(response);
            assertEquals(42, (int) response.getIntId());
            assertEquals("GWT", response.getUserName());
            assertEquals(8L, (long) response.getLongField());
            assertEquals(com.google.gwt.requestfactory.shared.SimpleEnum.FOO,
                response.getEnumField());
            assertEquals(null, response.getBarField());
            finishTestAndReset();
          }
        });
  }

  public void testFetchEntityWithRelation() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).with("barField").fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            response = checkSerialization(response);
            assertEquals(42, (int) response.getIntId());
            assertEquals("GWT", response.getUserName());
            assertEquals(8L, (long) response.getLongField());
            assertEquals(com.google.gwt.requestfactory.shared.SimpleEnum.FOO,
                response.getEnumField());
            assertNotNull(response.getBarField());
            finishTestAndReset();
          }
        });
  }

  public void testFetchList() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findAll().fire(new Receiver<List<SimpleFooProxy>>() {
      @Override
      public void onSuccess(List<SimpleFooProxy> responseList) {
        SimpleFooProxy response = checkSerialization(responseList.get(0));
        assertEquals(42, (int) response.getIntId());
        assertEquals("GWT", response.getUserName());
        assertEquals(8L, (long) response.getLongField());
        assertEquals(com.google.gwt.requestfactory.shared.SimpleEnum.FOO,
            response.getEnumField());
        finishTestAndReset();
      }
    });
  }

  public void testFetchSet() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleBarRequest().findAsSet().fire(new Receiver<Set<SimpleBarProxy>>() {
      @Override
      public void onSuccess(Set<SimpleBarProxy> response) {
        assertEquals(2, response.size());
        finishTestAndReset();
      }
    });
  }

  public void testFindFindEdit() {
    delayTestFinish(DELAY_TEST_FINISH);

    final SimpleFooEventHandler<SimpleFooProxy> handler = new SimpleFooEventHandler<SimpleFooProxy>();
    EntityProxyChange.registerForProxyType(req.getEventBus(),
        SimpleFooProxy.class, handler);

    req.simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {

          @Override
          public void onSuccess(SimpleFooProxy newFoo) {
            newFoo = checkSerialization(newFoo);
            assertEquals(1, handler.updateEventCount);
            assertEquals(1, handler.totalEventCount);

            req.simpleFooRequest().findSimpleFooById(999L).fire(
                new Receiver<SimpleFooProxy>() {

                  @Override
                  public void onSuccess(SimpleFooProxy newFoo) {
                    newFoo = checkSerialization(newFoo);
                    // no events are fired second time.
                    assertEquals(1, handler.updateEventCount);
                    assertEquals(1, handler.totalEventCount);
                    SimpleFooRequest context = req.simpleFooRequest();
                    final Request<Void> mutateRequest = context.persist().using(
                        newFoo);
                    newFoo = context.edit(newFoo);
                    newFoo.setUserName("Ray");
                    mutateRequest.fire(new Receiver<Void>() {
                      @Override
                      public void onSuccess(Void response) {
                        // events fired on updates.
                        assertEquals(2, handler.updateEventCount);
                        assertEquals(2, handler.totalEventCount);

                        finishTestAndReset();
                      }
                    });
                  }
                });
          }
        });
  }

  public void testForwardReferenceDecode() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().getTripletReference().with(
        "selfOneToManyField.selfOneToManyField.fooField").fire(
        new Receiver<SimpleFooProxy>() {
          public void onSuccess(SimpleFooProxy response) {
            response = checkSerialization(response);
            assertNotNull(response.getSelfOneToManyField().get(0));
            assertNotNull(response.getSelfOneToManyField().get(0).getSelfOneToManyField());
            assertNotNull(response.getSelfOneToManyField().get(0).getSelfOneToManyField().get(
                0));
            assertNotNull(response.getSelfOneToManyField().get(0).getSelfOneToManyField().get(
                0).getFooField());
            finishTestAndReset();
          }
        });
  }

  public void testGetEventBus() {
    assertEquals(eventBus, req.getEventBus());
  }

  public void testGetListLongId() {
    delayTestFinish(DELAY_TEST_FINISH);

    // Long ids
    simpleFooRequest().findAll().with("barField.userName").fire(
        new Receiver<List<SimpleFooProxy>>() {
          @Override
          public void onSuccess(List<SimpleFooProxy> response) {
            assertEquals(2, response.size());
            for (SimpleFooProxy foo : response) {
              foo = checkSerialization(foo);
              assertNotNull(foo.stableId());
              assertEquals("FOO", foo.getBarField().getUserName());
            }
            finishTestAndReset();
          }
        });
  }

  public void testGetListStringId() {
    delayTestFinish(DELAY_TEST_FINISH);

    // String ids
    simpleBarRequest().findAll().fire(new Receiver<List<SimpleBarProxy>>() {
      @Override
      public void onSuccess(List<SimpleBarProxy> response) {
        assertEquals(2, response.size());
        for (SimpleBarProxy bar : response) {
          bar = checkSerialization(bar);
          assertNotNull(bar.stableId());
          finishTestAndReset();
        }
        finishTestAndReset();
      }
    });
  }

  public void testHistoryToken() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleBarRequest context = simpleBarRequest();
    final SimpleBarProxy foo = context.create(SimpleBarProxy.class);
    final EntityProxyId<SimpleBarProxy> futureId = foo.stableId();
    final String futureToken = req.getHistoryToken(futureId);

    // Check that a newly-created object's token can be found
    assertEquals(futureId, req.getProxyId(futureToken));
    assertEquals(futureId.getProxyClass(), req.getProxyClass(futureToken));

    Request<SimpleBarProxy> fooReq = context.persistAndReturnSelf().using(foo);
    fooReq.fire(new Receiver<SimpleBarProxy>() {
      @Override
      public void onSuccess(SimpleBarProxy returned) {
        returned = checkSerialization(returned);
        EntityProxyId<SimpleBarProxy> persistedId = returned.stableId();
        String persistedToken = req.getHistoryToken(returned.stableId());

        // Expect variations after persist
        assertFalse(futureToken.equals(persistedToken));

        // Make sure the token is stable after persist using the future id
        assertEquals(persistedToken, req.getHistoryToken(futureId));

        // Check that the persisted object can be found with future token
        assertEquals(futureId, req.getProxyId(futureToken));
        assertEquals(futureId, req.getProxyId(persistedToken));
        assertEquals(futureId.getProxyClass(),
            req.getProxyClass(persistedToken));

        assertEquals(persistedId, req.getProxyId(futureToken));
        assertEquals(persistedId, req.getProxyId(persistedToken));
        assertEquals(persistedId.getProxyClass(),
            req.getProxyClass(futureToken));

        finishTestAndReset();
      }
    });
  }

  public void testInstanceServiceRequest() {
    delayTestFinish(DELAY_TEST_FINISH);
    req.instanceServiceRequest().add(5).fire(new Receiver<Integer>() {
      @Override
      public void onSuccess(Integer response) {
        assertEquals(10, (int) response);
        finishTestAndReset();
      }
    });
  }

  public void testInstanceServiceRequestByName() {
    delayTestFinish(DELAY_TEST_FINISH);
    req.instanceServiceRequestByName().add(5).fire(new Receiver<Integer>() {
      @Override
      public void onSuccess(Integer response) {
        assertEquals(10, (int) response);
        finishTestAndReset();
      }
    });
  }

  /**
   * Make sure our stock RF logging service keeps receiving.
   */
  public void testLoggingService() {
    delayTestFinish(DELAY_TEST_FINISH);
    String logRecordJson = new StringBuilder("{") //
    .append("\"level\": \"ALL\", ") //
    .append("\"loggerName\": \"logger\", ") //
    .append("\"msg\": \"Hi mom\", ") //
    .append("\"timestamp\": \"1234567890\",") //
    .append("\"thrown\": {}") //
    .append("}") //
    .toString();

    req.loggingRequest().logMessage(logRecordJson).fire(new Receiver<Void>() {
      @Override
      public void onSuccess(Void response) {
        finishTestAndReset();
      }
    });
  }

  /*
   * tests that (a) any method can have a side effect that is handled correctly.
   * (b) instance methods are handled correctly and (c) a request cannot be
   * reused after a successful response is received. (Yet?)
   */
  public void testMethodWithSideEffects() {
    delayTestFinish(DELAY_TEST_FINISH);

    final SimpleFooEventHandler<SimpleFooProxy> handler = new SimpleFooEventHandler<SimpleFooProxy>();
    EntityProxyChange.registerForProxyType(req.getEventBus(),
        SimpleFooProxy.class, handler);

    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {

          @Override
          public void onSuccess(SimpleFooProxy newFoo) {
            newFoo = checkSerialization(newFoo);
            assertEquals(1, handler.updateEventCount);
            assertEquals(1, handler.totalEventCount);
            SimpleFooRequest context = simpleFooRequest();
            final Request<Long> mutateRequest = context.countSimpleFooWithUserNameSideEffect().using(
                newFoo);
            newFoo = context.edit(newFoo);
            newFoo.setUserName("Ray");
            mutateRequest.fire(new Receiver<Long>() {
              @Override
              public void onSuccess(Long response) {
                assertCannotFire(mutateRequest);
                assertEquals(new Long(2L), response);
                assertEquals(2, handler.updateEventCount);
                assertEquals(2, handler.totalEventCount);

                // confirm that the instance method did have the desired
                // sideEffect.
                simpleFooRequest().findSimpleFooById(999L).fire(
                    new Receiver<SimpleFooProxy>() {
                      @Override
                      public void onSuccess(SimpleFooProxy finalFoo) {
                        finalFoo = checkSerialization(finalFoo);
                        assertEquals("Ray", finalFoo.getUserName());
                        assertEquals(2, handler.updateEventCount);
                        assertEquals(2, handler.totalEventCount);
                        finishTestAndReset();
                      }
                    });
              }

            });

            try {
              newFoo.setUserName("Barney");
              fail();
            } catch (IllegalStateException e) {
              /* pass, cannot change a request that is in flight */
            }
          }
        });
  }

  public void testMultipleEdits() {
    RequestContext c1 = req.simpleFooRequest();
    SimpleFooProxy proxy = c1.create(SimpleFooProxy.class);
    // Re-editing is idempotent
    assertSame(proxy, c1.edit(c1.edit(proxy)));

    // Should not allow "crossing the steams"
    RequestContext c2 = req.simpleFooRequest();
    try {
      c2.edit(proxy);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  /**
   * Ensures that a service method can respond with a null value.
   */
  public void testNullEntityProxyResult() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().returnNullSimpleFoo().fire(new NullReceiver());
  }

  /**
   * Test that a null value can be sent in a request.
   */
  public void testNullListRequest() {
    delayTestFinish(DELAY_TEST_FINISH);
    final Request<Void> fooReq = req.simpleFooRequest().receiveNullList(null);
    fooReq.fire(new Receiver<Void>() {
      @Override
      public void onSuccess(Void v) {
        finishTestAndReset();
      }
    });
  }

  /**
   * Ensures that a service method can respond with a null value.
   */
  public void testNullListResult() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().returnNullList().fire(new NullReceiver());
  }

  /**
   * Test that a null value can be sent in a request.
   */
  public void testNullSimpleFooRequest() {
    delayTestFinish(DELAY_TEST_FINISH);
    final Request<Void> fooReq = req.simpleFooRequest().receiveNullSimpleFoo(
        null);
    fooReq.fire(new Receiver<Void>() {
      @Override
      public void onSuccess(Void v) {
        finishTestAndReset();
      }
    });
  }

  /**
   * Test that a null value can be sent to an instance method.
   */
  public void testNullStringInstanceRequest() {
    delayTestFinish(DELAY_TEST_FINISH);

    // Get a valid proxy entity.
    req.simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            response = checkSerialization(response);
            Request<Void> fooReq = req.simpleFooRequest().receiveNull(null).using(
                response);
            fooReq.fire(new Receiver<Void>() {
              @Override
              public void onSuccess(Void v) {
                finishTestAndReset();
              }
            });
          }
        });
  }

  /**
   * Test that a null value can be sent in a request.
   */
  public void testNullStringRequest() {
    delayTestFinish(DELAY_TEST_FINISH);
    final Request<Void> fooReq = req.simpleFooRequest().receiveNullString(null);
    fooReq.fire(new Receiver<Void>() {
      @Override
      public void onSuccess(Void v) {
        finishTestAndReset();
      }
    });
  }

  /**
   * Ensures that a service method can respond with a null value.
   */
  public void testNullStringResult() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().returnNullString().fire(new NullReceiver());
  }

  /**
   * Test that a null value can be sent within a list of entities.
   */
  public void testNullValueInEntityListRequest() {
    delayTestFinish(DELAY_TEST_FINISH);

    // Get a valid proxy entity.
    req.simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            response = checkSerialization(response);
            List<SimpleFooProxy> list = new ArrayList<SimpleFooProxy>();
            list.add(response); // non-null
            list.add(null); // null
            final Request<Void> fooReq = req.simpleFooRequest().receiveNullValueInEntityList(
                list);
            fooReq.fire(new Receiver<Void>() {
              @Override
              public void onSuccess(Void v) {
                finishTestAndReset();
              }
            });
          }
        });
  }

  /**
   * Test that a null value can be sent within a list of objects.
   */
  public void testNullValueInIntegerListRequest() {
    delayTestFinish(DELAY_TEST_FINISH);
    List<Integer> list = Arrays.asList(new Integer[] {1, 2, null});
    final Request<Void> fooReq = req.simpleFooRequest().receiveNullValueInIntegerList(
        list);
    fooReq.fire(new Receiver<Void>() {
      @Override
      public void onSuccess(Void v) {
        finishTestAndReset();
      }
    });
  }

  /**
   * Test that a null value can be sent within a list of strings.
   */
  public void testNullValueInStringListRequest() {
    delayTestFinish(DELAY_TEST_FINISH);
    List<String> list = Arrays.asList(new String[] {"nonnull", "null", null});
    final Request<Void> fooReq = req.simpleFooRequest().receiveNullValueInStringList(
        list);
    fooReq.fire(new Receiver<Void>() {
      @Override
      public void onSuccess(Void v) {
        finishTestAndReset();
      }
    });
  }

  public void testPersistAllValueTypes() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleFooRequest r = simpleFooRequest();
    SimpleFooProxy f = r.create(SimpleFooProxy.class);

    f.setUserName("user name");
    f.setByteField((byte) 100);
    f.setShortField((short) 12345);
    f.setFloatField(1234.56f);
    f.setDoubleField(1.2345);
    f.setLongField(1234L);
    f.setBoolField(false);
    f.setOtherBoolField(true);
    f.setCreated(new Date(397387389L));

    r.persistAndReturnSelf().using(f).fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy f) {
        f = checkSerialization(f);
        assertEquals("user name", f.getUserName());
        assertEquals(Byte.valueOf((byte) 100), f.getByteField());
        assertEquals(Short.valueOf((short) 12345), f.getShortField());
        assertEquals(0, (int) Math.rint(123456f - 100 * f.getFloatField()));
        assertEquals(Double.valueOf(1.2345), f.getDoubleField());
        assertEquals(Long.valueOf(1234L), f.getLongField());
        assertFalse(f.getBoolField());
        assertTrue(f.getOtherBoolField());
        assertEquals(new Date(397387389L), f.getCreated());

        finishTestAndReset();
      }
    });
  }

  public void testPersistExistingEntityExistingRelation() {
    delayTestFinish(DELAY_TEST_FINISH);

    // Retrieve a Bar
    simpleBarRequest().findSimpleBarById("999L").fire(
        new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy response) {
            final SimpleBarProxy barProxy = checkSerialization(response);
            // Retrieve a Foo
            simpleFooRequest().findSimpleFooById(999L).fire(
                new Receiver<SimpleFooProxy>() {
                  @Override
                  public void onSuccess(SimpleFooProxy fooProxy) {
                    fooProxy = checkSerialization(fooProxy);
                    SimpleFooRequest context = simpleFooRequest();
                    fooProxy = context.edit(fooProxy);
                    // Make the Foo point to the Bar
                    fooProxy.setBarField(barProxy);
                    fooProxy.setUserName("Hello");
                    fooProxy.setByteField((byte) 55);
                    context.persistAndReturnSelf().using(fooProxy).with(
                        "barField").fire(new Receiver<SimpleFooProxy>() {
                      @Override
                      public void onSuccess(SimpleFooProxy received) {
                        received = checkSerialization(received);
                        // Check that Foo points to Bar
                        assertNotNull(received.getBarField());
                        assertEquals(barProxy.stableId(),
                            received.getBarField().stableId());
                        assertEquals("Hello", received.getUserName());
                        assertTrue(55 == received.getByteField());

                        // Unset the association
                        SimpleFooRequest context = simpleFooRequest();
                        received = context.edit(received);
                        received.setBarField(null);
                        received.setUserName(null);
                        received.setByteField(null);
                        context.persistAndReturnSelf().using(received).fire(
                            new Receiver<SimpleFooProxy>() {
                              @Override
                              public void onSuccess(SimpleFooProxy response) {
                                response = checkSerialization(response);
                                assertNull(response.getBarField());
                                assertNull(response.getUserName());
                                assertNull(response.getByteField());
                                finishTestAndReset();
                              }
                            });
                      }
                    });
                  }
                });
          }
        });
  }

  /*
   * Find Entity Create Entity2 Relate Entity2 to Entity Persist Entity
   */
  public void testPersistExistingEntityNewRelation() {
    delayTestFinish(DELAY_TEST_FINISH);
    // Make a new bar
    SimpleBarRequest context = simpleBarRequest();
    SimpleBarProxy makeABar = context.create(SimpleBarProxy.class);
    Request<SimpleBarProxy> persistRequest = context.persistAndReturnSelf().using(
        makeABar);
    makeABar = context.edit(makeABar);
    makeABar.setUserName("Amit");

    persistRequest.fire(new Receiver<SimpleBarProxy>() {
      @Override
      public void onSuccess(SimpleBarProxy response) {
        final SimpleBarProxy persistedBar = checkSerialization(response);

        // It was made, now find a foo to assign it to
        simpleFooRequest().findSimpleFooById(999L).fire(
            new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);

                // Found the foo, edit it
                SimpleFooRequest context = simpleFooRequest();
                Request<Void> fooReq = context.persist().using(response);
                response = context.edit(response);
                response.setBarField(persistedBar);
                fooReq.fire(new Receiver<Void>() {
                  @Override
                  public void onSuccess(Void response) {

                    // Foo was persisted, fetch it again check the goods
                    simpleFooRequest().findSimpleFooById(999L).with(
                        "barField.userName").fire(
                        new Receiver<SimpleFooProxy>() {

                          // Here it is
                          @Override
                          public void onSuccess(SimpleFooProxy finalFooProxy) {
                            finalFooProxy = checkSerialization(finalFooProxy);
                            assertEquals("Amit",
                                finalFooProxy.getBarField().getUserName());
                            finishTestAndReset();
                          }
                        });
                  }
                });
              }
            });
      }
    });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */

  /**
   * Ensure that a relationship can be set up between two newly-created objects.
   */
  public void testPersistFutureToFuture() {
    delayTestFinish(DELAY_TEST_FINISH);
    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy newFoo = context.create(SimpleFooProxy.class);
    final SimpleBarProxy newBar = context.create(SimpleBarProxy.class);

    Request<SimpleFooProxy> fooReq = context.persistAndReturnSelf().using(
        newFoo).with("barField");
    newFoo = context.edit(newFoo);
    newFoo.setBarField(newBar);

    fooReq.fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        assertNotNull(response.getBarField());
        assertEquals(newBar.stableId(), response.getBarField().stableId());
        finishTestAndReset();
      }
    });
  }

  /*
   * Find Entity2 Create Entity, Persist Entity Relate Entity2 to Entity Persist
   * Entity
   */
  public void testPersistNewEntityExistingRelation() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy newFoo = context.edit(context.create(SimpleFooProxy.class));
    final Request<Void> fooReq = context.persist().using(newFoo);

    newFoo.setUserName("Ray");

    final SimpleFooProxy finalFoo = newFoo;
    simpleBarRequest().findSimpleBarById("999L").fire(
        new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy response) {
            response = checkSerialization(response);
            finalFoo.setBarField(response);
            fooReq.fire(new Receiver<Void>() {
              @Override
              public void onSuccess(Void response) {
                simpleFooRequest().findSimpleFooById(999L).fire(
                    new Receiver<SimpleFooProxy>() {
                      @Override
                      public void onSuccess(SimpleFooProxy finalFooProxy) {
                        finalFooProxy = checkSerialization(finalFooProxy);
                        // newFoo hasn't been persisted, so userName is the old
                        // value.
                        assertEquals("GWT", finalFooProxy.getUserName());
                        finishTestAndReset();
                      }
                    });
              }
            });
          }
        });
  }

  /*
   * Create Entity, Persist Entity Create Entity2, Perist Entity2 relate Entity2
   * to Entity Persist
   */
  public void testPersistNewEntityNewRelation() {
    delayTestFinish(DELAY_TEST_FINISH);
    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy newFoo = context.create(SimpleFooProxy.class);

    final Request<SimpleFooProxy> fooReq = context.persistAndReturnSelf().using(
        newFoo);
    newFoo = context.edit(newFoo);
    newFoo.setUserName("Ray");

    SimpleBarRequest context2 = simpleBarRequest();
    SimpleBarProxy newBar = context2.create(SimpleBarProxy.class);
    final Request<SimpleBarProxy> barReq = context2.persistAndReturnSelf().using(
        newBar);
    newBar = context2.edit(newBar);
    newBar.setUserName("Amit");

    fooReq.fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(final SimpleFooProxy response) {
        final SimpleFooProxy persistedFoo = checkSerialization(response);
        barReq.fire(new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy response) {
            final SimpleBarProxy persistedBar = checkSerialization(response);

            assertEquals("Ray", persistedFoo.getUserName());
            SimpleFooRequest context = simpleFooRequest();
            final Request<Void> fooReq2 = context.persist().using(persistedFoo);
            SimpleFooProxy editablePersistedFoo = context.edit(persistedFoo);
            editablePersistedFoo.setBarField(persistedBar);
            fooReq2.fire(new Receiver<Void>() {
              @Override
              public void onSuccess(Void response) {
                req.simpleFooRequest().findSimpleFooById(persistedFoo.getId()).with(
                    "barField.userName").fire(new Receiver<SimpleFooProxy>() {
                  @Override
                  public void onSuccess(SimpleFooProxy finalFooProxy) {
                    finalFooProxy = checkSerialization(finalFooProxy);
                    assertEquals("Amit",
                        finalFooProxy.getBarField().getUserName());
                    finishTestAndReset();
                  }
                });
              }
            });
          }
        });
      }
    });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */
  public void testPersistOneToManyExistingEntityExistingRelation() {
    delayTestFinish(DELAY_TEST_FINISH);

    simpleBarRequest().findSimpleBarById("999L").fire(
        new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy response) {
            final SimpleBarProxy barProxy = checkSerialization(response);
            simpleFooRequest().findSimpleFooById(999L).with("oneToManyField").fire(
                new Receiver<SimpleFooProxy>() {
                  @Override
                  public void onSuccess(SimpleFooProxy fooProxy) {
                    fooProxy = checkSerialization(fooProxy);

                    SimpleFooRequest context = simpleFooRequest();
                    Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                        fooProxy).with("oneToManyField");
                    fooProxy = context.edit(fooProxy);

                    List<SimpleBarProxy> barProxyList = fooProxy.getOneToManyField();
                    final int listCount = barProxyList.size();
                    barProxyList.add(barProxy);
                    updReq.fire(new Receiver<SimpleFooProxy>() {
                      @Override
                      public void onSuccess(SimpleFooProxy response) {
                        response = checkSerialization(response);
                        assertEquals(response.getOneToManyField().size(),
                            listCount + 1);
                        assertContains(response.getOneToManyField(), barProxy);
                        finishTestAndReset();
                      }
                    });
                  }
                });
          }
        });
  }

  public void testPersistRecursiveRelation() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy rayFoo = context.create(SimpleFooProxy.class);
    final Request<SimpleFooProxy> persistRay = context.persistAndReturnSelf().using(
        rayFoo);
    rayFoo = context.edit(rayFoo);
    rayFoo.setUserName("Ray");
    rayFoo.setFooField(rayFoo);
    persistRay.fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        finishTestAndReset();
      }
    });
  }

  public void testPersistRelation() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy rayFoo = context.create(SimpleFooProxy.class);
    final Request<SimpleFooProxy> persistRay = context.persistAndReturnSelf().using(
        rayFoo);
    rayFoo = context.edit(rayFoo);
    rayFoo.setUserName("Ray");

    persistRay.fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        final SimpleFooProxy persistedRay = checkSerialization(response);
        SimpleBarRequest context = simpleBarRequest();
        SimpleBarProxy amitBar = context.create(SimpleBarProxy.class);
        final Request<SimpleBarProxy> persistAmit = context.persistAndReturnSelf().using(
            amitBar);
        amitBar = context.edit(amitBar);
        amitBar.setUserName("Amit");

        persistAmit.fire(new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy response) {
            response = checkSerialization(response);

            SimpleFooRequest context = simpleFooRequest();
            final Request<SimpleFooProxy> persistRelationship = context.persistAndReturnSelf().using(
                persistedRay).with("barField");
            SimpleFooProxy newRec = context.edit(persistedRay);
            newRec.setBarField(response);

            persistRelationship.fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                assertEquals("Amit", response.getBarField().getUserName());
                finishTestAndReset();
              }
            });
          }
        });
      }
    });
  }

  public void testPersistSelfOneToManyExistingEntityExistingRelation() {
    delayTestFinish(DELAY_TEST_FINISH);

    simpleFooRequest().findSimpleFooById(999L).with("selfOneToManyField").fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy fooProxy) {
            fooProxy = checkSerialization(fooProxy);
            SimpleFooRequest context = simpleFooRequest();
            Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                fooProxy).with("selfOneToManyField");
            fooProxy = context.edit(fooProxy);
            List<SimpleFooProxy> fooProxyList = fooProxy.getSelfOneToManyField();
            final int listCount = fooProxyList.size();
            fooProxyList.add(fooProxy);
            updReq.fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                assertEquals(response.getSelfOneToManyField().size(),
                    listCount + 1);
                assertContains(response.getSelfOneToManyField(), response);
                finishTestAndReset();
              }
            });
          }
        });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */

  public void testPersistValueList() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy fooProxy) {
            fooProxy = checkSerialization(fooProxy);
            SimpleFooRequest context = simpleFooRequest();
            Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                fooProxy);
            fooProxy = context.edit(fooProxy);
            fooProxy.getNumberListField().add(100);
            updReq.fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                assertTrue(response.getNumberListField().contains(100));
                finishTestAndReset();
              }
            });
          }
        });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */
  public void testPersistValueListNull() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy fooProxy) {
            fooProxy = checkSerialization(fooProxy);
            SimpleFooRequest context = simpleFooRequest();
            Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                fooProxy);
            fooProxy = context.edit(fooProxy);

            fooProxy.setNumberListField(null);
            updReq.fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                List<Integer> list = response.getNumberListField();
                assertNull(list);
                finishTestAndReset();
              }
            });
          }
        });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */
  public void testPersistValueListRemove() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy fooProxy) {
            fooProxy = checkSerialization(fooProxy);
            SimpleFooRequest context = simpleFooRequest();
            Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                fooProxy);
            fooProxy = context.edit(fooProxy);
            final int oldValue = fooProxy.getNumberListField().remove(0);
            updReq.fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                assertFalse(response.getNumberListField().contains(oldValue));
                finishTestAndReset();
              }
            });
          }
        });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */
  public void testPersistValueListReplace() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy fooProxy) {
            fooProxy = checkSerialization(fooProxy);
            SimpleFooRequest context = simpleFooRequest();
            Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                fooProxy);
            fooProxy = context.edit(fooProxy);
            final ArrayList<Integer> al = new ArrayList<Integer>();
            al.add(5);
            al.add(8);
            al.add(13);
            fooProxy.setNumberListField(al);
            updReq.fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                List<Integer> list = response.getNumberListField();
                assertEquals(5, (int) list.get(0));
                assertEquals(8, (int) list.get(1));
                assertEquals(13, (int) list.get(2));
                finishTestAndReset();
              }
            });
          }
        });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */
  public void testPersistValueListReverse() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy fooProxy) {
            fooProxy = checkSerialization(fooProxy);
            SimpleFooRequest context = simpleFooRequest();
            Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                fooProxy);
            fooProxy = context.edit(fooProxy);
            final ArrayList<Integer> al = new ArrayList<Integer>();
            List<Integer> listField = fooProxy.getNumberListField();
            al.addAll(listField);
            Collections.reverse(listField);
            updReq.fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                Collections.reverse(al);
                assertTrue(response.getNumberListField().equals(al));
                finishTestAndReset();
              }
            });
          }
        });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */
  public void testPersistValueListSetIndex() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy fooProxy) {
            fooProxy = checkSerialization(fooProxy);
            SimpleFooRequest context = simpleFooRequest();
            Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                fooProxy);
            fooProxy = context.edit(fooProxy);
            fooProxy.getNumberListField().set(0, 10);
            updReq.fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                assertTrue(response.getNumberListField().get(0) == 10);
                finishTestAndReset();
              }
            });
          }
        });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */
  public void testPersistValueSetAddNew() {
    delayTestFinish(DELAY_TEST_FINISH);
    SimpleBarRequest context = simpleBarRequest();
    SimpleBarProxy newBar = context.create(SimpleBarProxy.class);

    context.persistAndReturnSelf().using(newBar).fire(
        new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy response) {
            final SimpleBarProxy barProxy = checkSerialization(response);
            simpleFooRequest().findSimpleFooById(999L).with("oneToManySetField").fire(
                new Receiver<SimpleFooProxy>() {
                  @Override
                  public void onSuccess(SimpleFooProxy fooProxy) {
                    fooProxy = checkSerialization(fooProxy);
                    SimpleFooRequest context = simpleFooRequest();
                    Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                        fooProxy).with("oneToManySetField");
                    fooProxy = context.edit(fooProxy);

                    Set<SimpleBarProxy> setField = fooProxy.getOneToManySetField();
                    final int listCount = setField.size();
                    setField.add(barProxy);
                    updReq.fire(new Receiver<SimpleFooProxy>() {
                      @Override
                      public void onSuccess(SimpleFooProxy response) {
                        response = checkSerialization(response);
                        assertEquals(listCount + 1,
                            response.getOneToManySetField().size());
                        assertContains(response.getOneToManySetField(),
                            barProxy);
                        finishTestAndReset();
                      }
                    });
                  }
                });
          }
        });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */
  public void testPersistValueSetAlreadyExists() {
    delayTestFinish(DELAY_TEST_FINISH);

    simpleBarRequest().findSimpleBarById("1L").fire(
        new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy response) {
            final SimpleBarProxy barProxy = checkSerialization(response);

            simpleFooRequest().findSimpleFooById(999L).with("oneToManySetField").fire(
                new Receiver<SimpleFooProxy>() {
                  @Override
                  public void onSuccess(SimpleFooProxy fooProxy) {
                    fooProxy = checkSerialization(fooProxy);
                    SimpleFooRequest context = simpleFooRequest();
                    Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                        fooProxy).with("oneToManySetField");
                    fooProxy = context.edit(fooProxy);

                    Set<SimpleBarProxy> setField = fooProxy.getOneToManySetField();
                    final int listCount = setField.size();
                    assertContains(setField, barProxy);
                    setField.add(barProxy);
                    updReq.fire(new Receiver<SimpleFooProxy>() {
                      @Override
                      public void onSuccess(SimpleFooProxy response) {
                        response = checkSerialization(response);
                        assertEquals(response.getOneToManySetField().size(),
                            listCount);
                        assertContains(response.getOneToManySetField(),
                            barProxy);
                        finishTestAndReset();
                      }
                    });
                  }
                });
          }
        });
  }

  /*
   * TODO: all these tests should check the final values. It will be easy when
   * we have better persistence than the singleton pattern.
   */
  public void testPersistValueSetRemove() {
    delayTestFinish(DELAY_TEST_FINISH);

    simpleBarRequest().findSimpleBarById("1L").fire(
        new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy response) {
            final SimpleBarProxy barProxy = checkSerialization(response);
            simpleFooRequest().findSimpleFooById(999L).with("oneToManySetField").fire(
                new Receiver<SimpleFooProxy>() {
                  @Override
                  public void onSuccess(SimpleFooProxy fooProxy) {
                    fooProxy = checkSerialization(fooProxy);
                    SimpleFooRequest context = simpleFooRequest();
                    Request<SimpleFooProxy> updReq = context.persistAndReturnSelf().using(
                        fooProxy).with("oneToManySetField");
                    fooProxy = context.edit(fooProxy);

                    Set<SimpleBarProxy> setField = fooProxy.getOneToManySetField();
                    final int listCount = setField.size();
                    assertContains(setField, barProxy);
                    setField.remove(context.edit(barProxy));
                    assertNotContains(setField, barProxy);
                    updReq.fire(new Receiver<SimpleFooProxy>() {
                      @Override
                      public void onSuccess(SimpleFooProxy response) {
                        response = checkSerialization(response);
                        assertEquals(listCount - 1,
                            response.getOneToManySetField().size());
                        assertNotContains(response.getOneToManySetField(),
                            barProxy);
                        finishTestAndReset();
                      }
                    });
                  }
                });
          }
        });
  }

  public void testPrimitiveList() {
    delayTestFinish(DELAY_TEST_FINISH);
    final Request<List<Integer>> fooReq = simpleFooRequest().getNumberList();
    fooReq.fire(new Receiver<List<Integer>>() {
      @Override
      public void onSuccess(List<Integer> response) {
        assertEquals(3, response.size());
        assertEquals(1, (int) response.get(0));
        assertEquals(2, (int) response.get(1));
        assertEquals(3, (int) response.get(2));
        finishTestAndReset();
      }
    });
  }

  public void testPrimitiveListAsParameter() {
    delayTestFinish(DELAY_TEST_FINISH);
    final Request<SimpleFooProxy> fooReq = simpleFooRequest().findSimpleFooById(
        999L);
    fooReq.fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        final Request<Integer> sumReq = simpleFooRequest().sum(
            Arrays.asList(1, 2, 3)).using(response);
        sumReq.fire(new Receiver<Integer>() {
          @Override
          public void onSuccess(Integer response) {
            assertEquals(6, response.intValue());
            finishTestAndReset();
          }
        });
      }
    });
  }

  public void testPrimitiveListBooleanAsParameter() {
    delayTestFinish(DELAY_TEST_FINISH);

    Request<Boolean> procReq = simpleFooRequest().processBooleanList(
        Arrays.asList(true, false));

    procReq.fire(new Receiver<Boolean>() {
      @Override
      public void onSuccess(Boolean response) {
        assertEquals(true, (boolean) response);
        finishTestAndReset();
      }
    });
  }

  public void testPrimitiveListBigDecimalAsParameter() {
    delayTestFinish(DELAY_TEST_FINISH);

    // Keep these values in sync with SimpleFoo.processBigIntegerList
    final List<BigDecimal> testList = new ArrayList<BigDecimal>();
    testList.add(BigDecimal.TEN);
    testList.add(new BigDecimal("12345.6789") {
      // This is an anonymous subtype
    });
    simpleFooRequest().processBigDecimalList(testList).fire(
        new Receiver<List<BigDecimal>>() {
          @Override
          public void onSuccess(List<BigDecimal> response) {
            // Check upcasted values only
            assertEquals(testList, response);
            finishTestAndReset();
          }
        });
  }

  public void testPrimitiveListBigIntegerAsParameter() {
    delayTestFinish(DELAY_TEST_FINISH);

    // Keep these values in sync with SimpleFoo.processBigIntegerList
    final List<BigInteger> testList = new ArrayList<BigInteger>();
    testList.add(BigInteger.TEN);
    testList.add(new BigInteger("12345") {
      // This is an anonymous subtype
    });
    simpleFooRequest().processBigIntegerList(testList).fire(
        new Receiver<List<BigInteger>>() {
          @Override
          public void onSuccess(List<BigInteger> response) {
            // Check upcasted values only
            assertEquals(testList, response);
            finishTestAndReset();
          }
        });
  }

  @SuppressWarnings("deprecation")
  public void testPrimitiveListDateAsParameter() {
    delayTestFinish(DELAY_TEST_FINISH);

    // Keep these values in sync with SimpleFoo.processDateList
    Date date = new Date(90, 0, 1);
    java.sql.Date sqlDate = new java.sql.Date(90, 0, 2);
    Time sqlTime = new Time(1, 2, 3);
    Timestamp sqlTimestamp = new Timestamp(12345L);
    final List<Date> testList = Arrays.asList(date, sqlDate, sqlTime,
        sqlTimestamp);
    simpleFooRequest().processDateList(testList).fire(
        new Receiver<List<Date>>() {
          @Override
          public void onSuccess(List<Date> response) {
            // Check upcasted values only
            assertEquals(testList.size(), response.size());
            Iterator<Date> expected = testList.iterator();
            Iterator<Date> actual = response.iterator();
            while (expected.hasNext()) {
              assertEquals(expected.next().getTime(), actual.next().getTime());
            }
            finishTestAndReset();
          }
        });
  }

  public void testPrimitiveListEnumAsParameter() {
    delayTestFinish(DELAY_TEST_FINISH);

    Request<SimpleEnum> procReq = simpleFooRequest().processEnumList(
        Arrays.asList(SimpleEnum.BAR));

    procReq.fire(new Receiver<SimpleEnum>() {
      @Override
      public void onSuccess(SimpleEnum response) {
        assertEquals(SimpleEnum.BAR, response);
        finishTestAndReset();
      }
    });
  }

  public void testPrimitiveParameter() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().add(3, 5).fire(new Receiver<Integer>() {
      @Override
      public void onSuccess(Integer response) {
        assertTrue(8 == response);
        finishTestAndReset();
      }
    });
  }

  public void testPrimitiveSet() {
    delayTestFinish(DELAY_TEST_FINISH);
    final Request<Set<Integer>> fooReq = simpleFooRequest().getNumberSet();
    fooReq.fire(new Receiver<Set<Integer>>() {
      @Override
      public void onSuccess(Set<Integer> response) {
        assertEquals(3, response.size());
        assertTrue(response.contains(1));
        assertTrue(response.contains(2));
        assertTrue(response.contains(3));
        finishTestAndReset();
      }
    });
  }

  public void testPrimitiveString() {
    delayTestFinish(DELAY_TEST_FINISH);
    final String testString = "test\"string\'with\nstring\u2060characters\t";
    final Request<String> fooReq = simpleFooRequest().processString(testString);
    fooReq.fire(new Receiver<String>() {
      @Override
      public void onSuccess(String response) {
        assertEquals(testString, response);
        finishTestAndReset();
      }
    });
  }

  public void testProxyList() {
    delayTestFinish(DELAY_TEST_FINISH);
    final Request<SimpleFooProxy> fooReq = simpleFooRequest().findSimpleFooById(
        999L).with("oneToManyField");
    fooReq.fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        assertEquals(2, response.getOneToManyField().size());

        // Check lists of proxies returned from a mutable object are mutable
        response = simpleFooRequest().edit(response);
        response.getOneToManyField().get(0).setUserName("canMutate");
        finishTestAndReset();
      }
    });
  }

  public void testProxyListAsParameter() {
    delayTestFinish(DELAY_TEST_FINISH);
    final Request<SimpleFooProxy> fooReq = simpleFooRequest().findSimpleFooById(
        999L).with("selfOneToManyField");
    fooReq.fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        final SimpleFooProxy fooProxy = checkSerialization(response);
        final Request<String> procReq = simpleFooRequest().processList(
            fooProxy.getSelfOneToManyField()).using(fooProxy);
        procReq.fire(new Receiver<String>() {
          @Override
          public void onSuccess(String response) {
            assertEquals(fooProxy.getUserName(), response);
            finishTestAndReset();
          }
        });
      }
    });
  }

  public void testProxysAsInstanceMethodParams() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().findSimpleFooById(999L).fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            response = checkSerialization(response);
            SimpleFooRequest context = simpleFooRequest();
            SimpleBarProxy bar = context.create(SimpleBarProxy.class);
            Request<String> helloReq = context.hello(bar).using(response);
            bar = context.edit(bar);
            bar.setUserName("BAR");
            helloReq.fire(new Receiver<String>() {
              @Override
              public void onSuccess(String response) {
                assertEquals("Greetings BAR from GWT", response);
                finishTestAndReset();
              }
            });
          }
        });
  }

  public void testServerFailureCheckedException() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy newFoo = context.create(SimpleFooProxy.class);
    final Request<SimpleFooProxy> persistRequest = context.persistAndReturnSelf().using(
        newFoo);
    final SimpleFooProxy mutableFoo = context.edit(newFoo);
    // 43 is the crash causing magic number for a checked exception
    mutableFoo.setPleaseCrash(43);
    persistRequest.fire(new FooReciever(mutableFoo, persistRequest, null));
  }

  public void testServerFailureRuntimeException() {
    delayTestFinish(DELAY_TEST_FINISH);
    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy newFoo = context.create(SimpleFooProxy.class);
    final Request<SimpleFooProxy> persistRequest = context.persistAndReturnSelf().using(
        newFoo);
    final SimpleFooProxy mutableFoo = context.edit(newFoo);
    // 42 is the crash causing magic number for a runtime exception
    mutableFoo.setPleaseCrash(42);
    persistRequest.fire(new FooReciever(mutableFoo, persistRequest, null));
  }

  /**
   * Tests the behaviors of setters and their effects on getters.
   */
  public void testSetters() {
    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy foo = context.create(SimpleFooProxy.class);
    SimpleBarProxy bar = context.create(SimpleBarProxy.class);

    // Assert that uninitalize references are null
    assertNull(foo.getBarField());

    // Assert that objects are mutable after creation
    foo.setBarField(bar);

    assertSame(foo, context.edit(foo));
    foo.setBarField(bar);

    // Assert that the set value is retained
    SimpleBarProxy returnedBarField = foo.getBarField();
    assertNotNull(returnedBarField);
    assertEquals(bar.stableId(), returnedBarField.stableId());
    assertEquals(returnedBarField, foo.getBarField());
    assertSame(returnedBarField, foo.getBarField());

    // Getters called on mutable objects are also mutable
    returnedBarField.setUserName("userName");
    assertEquals("userName", returnedBarField.getUserName());
  }

  /**
   * There's plenty of special-case code for Collection properties, so they need
   * to be tested as well.
   */
  public void testSettersWithCollections() {
    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy foo = context.create(SimpleFooProxy.class);
    SimpleBarProxy bar = context.create(SimpleBarProxy.class);
    List<SimpleBarProxy> originalList = Collections.singletonList(bar);

    // Assert that uninitalize references are null
    assertNull(foo.getOneToManyField());

    // Assert that objects are mutable after creation
    foo.setOneToManyField(null);

    assertSame(foo, context.edit(foo));
    foo.setOneToManyField(originalList);
    // There's a "dummy" create case here; AbstractRequest, DVS is untestable

    // Quick sanity check on the behavior
    List<SimpleBarProxy> list = foo.getOneToManyField();
    assertNotSame(originalList, list);
    assertEquals(originalList, list);
    assertEquals(1, list.size());
    assertEquals(bar.stableId(), list.get(0).stableId());
    assertEquals(list, foo.getOneToManyField());

    // Assert that entities returned from editable list are mutable
    list.get(0).setUserName("userName");
  }

  public void testSettersWithMutableObject() {
    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy foo = context.create(SimpleFooProxy.class);
    foo = context.edit(foo);

    SimpleBarProxy immutableBar = context.create(SimpleBarProxy.class);
    SimpleBarProxy mutableBar = context.edit(immutableBar);
    mutableBar.setUserName("userName");
    foo.setBarField(mutableBar);

    // Creating a new editable object in the same request should read through
    context.edit(immutableBar).setUserName("Reset");
    assertEquals("Reset", foo.getBarField().getUserName());
  }

  public void testStableId() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleFooRequest context = simpleFooRequest();
    final SimpleFooProxy foo = context.create(SimpleFooProxy.class);
    final Object futureId = foo.getId();
    assertTrue(((SimpleEntityProxyId<?>) foo.stableId()).isEphemeral());
    Request<SimpleFooProxy> fooReq = context.persistAndReturnSelf().using(foo);

    final SimpleFooProxy newFoo = context.edit(foo);
    assertEquals(futureId, foo.getId());
    assertTrue(((SimpleEntityProxyId<?>) foo.stableId()).isEphemeral());
    assertEquals(futureId, newFoo.getId());
    assertTrue(((SimpleEntityProxyId<?>) newFoo.stableId()).isEphemeral());

    newFoo.setUserName("GWT basic user");
    fooReq.fire(new Receiver<SimpleFooProxy>() {

      @Override
      public void onSuccess(SimpleFooProxy response) {
        final SimpleFooProxy returned = checkSerialization(response);
        assertEquals(futureId, foo.getId());
        assertFalse(((SimpleEntityProxyId<?>) foo.stableId()).isEphemeral());
        assertEquals(futureId, newFoo.getId());
        assertFalse(((SimpleEntityProxyId<?>) newFoo.stableId()).isEphemeral());

        assertFalse(((SimpleEntityProxyId<?>) returned.stableId()).isEphemeral());

        checkStableIdEquals(foo, returned);
        checkStableIdEquals(newFoo, returned);
        SimpleFooRequest context = simpleFooRequest();
        Request<SimpleFooProxy> editRequest = context.persistAndReturnSelf().using(
            returned);
        final SimpleFooProxy editableFoo = context.edit(returned);
        editableFoo.setUserName("GWT power user");
        editRequest.fire(new Receiver<SimpleFooProxy>() {

          @Override
          public void onSuccess(SimpleFooProxy response) {
            response = checkSerialization(response);
            assertEquals(response.getId(), returned.getId());
            assertEquals("GWT power user", response.getUserName());
            checkStableIdEquals(editableFoo, response);
            finishTestAndReset();
          }
        });
      }
    });
  }

  /**
   * Check if a graph of unpersisted objects can be echoed.
   */
  public void testUnpersistedEchoComplexGraph() {
    delayTestFinish(DELAY_TEST_FINISH);
    final SimpleFooEventHandler<SimpleFooProxy> handler = new SimpleFooEventHandler<SimpleFooProxy>();
    EntityProxyChange.registerForProxyType(req.getEventBus(),
        SimpleFooProxy.class, handler);
    SimpleFooRequest context = req.simpleFooRequest();
    final SimpleBarProxy simpleBar = context.create(SimpleBarProxy.class);
    simpleBar.setUnpersisted(true);
    final SimpleFooProxy simpleFoo = context.create(SimpleFooProxy.class);
    simpleFoo.setUnpersisted(true);
    simpleFoo.setBarField(simpleBar);
    context.echoComplex(simpleFoo, simpleBar).with("barField").fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            // The reconstituted object may not have the same stable id
            checkStableIdEquals(simpleBar, response.getBarField());
            response = checkSerialization(response);
            assertEquals(0, handler.totalEventCount);
            checkStableIdEquals(simpleFoo, response);
            SimpleBarProxy responseBar = response.getBarField();
            assertNotNull(responseBar);
            finishTestAndReset();
          }
        });
  }

  /**
   * Check if an unpersisted object can be echoed.
   */
  public void testUnpersistedEchoObject() {
    delayTestFinish(DELAY_TEST_FINISH);
    final SimpleFooEventHandler<SimpleFooProxy> handler = new SimpleFooEventHandler<SimpleFooProxy>();
    EntityProxyChange.registerForProxyType(req.getEventBus(),
        SimpleFooProxy.class, handler);
    SimpleFooRequest context = req.simpleFooRequest();
    final SimpleFooProxy simpleFoo = context.create(SimpleFooProxy.class);
    simpleFoo.setUnpersisted(true);
    context.echo(simpleFoo).fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        assertEquals(0, handler.totalEventCount);
        checkStableIdEquals(simpleFoo, response);
        finishTestAndReset();
      }
    });
  }

  /**
   * Return an unpersisted object from a service method and echo it.
   */
  public void testUnpersistedObjectFetch() {
    delayTestFinish(DELAY_TEST_FINISH);
    req.simpleFooRequest().getUnpersistedInstance().fire(
        new Receiver<SimpleFooProxy>() {
          @Override
          public void onSuccess(SimpleFooProxy response) {
            final SimpleFooProxy created = checkSerialization(response);
            assertNotNull(created);
            assertTrue(created.getUnpersisted());
            req.simpleFooRequest().echo(created).fire(
                new Receiver<SimpleFooProxy>() {
                  @Override
                  public void onSuccess(SimpleFooProxy response) {
                    response = checkSerialization(response);
                    assertNotNull(response);
                    assertEquals(created.stableId(), response.stableId());
                    assertTrue(response.getUnpersisted());
                    finishTestAndReset();
                  }
                });
          }
        });
  }

  /**
   * This is analagous to FindServiceTest.testFetchDeletedEntity() only we're
   * trying to invoke a method on the deleted entity using a stale EntityProxy
   * reference on the client.
   */
  public void testUseOfDeletedEntity() {
    delayTestFinish(DELAY_TEST_FINISH);
    SimpleBarRequest context = simpleBarRequest();
    SimpleBarProxy willDelete = context.create(SimpleBarProxy.class);
    willDelete.setUserName("A");

    // Persist the newly-created object
    context.persistAndReturnSelf().using(willDelete).fire(
        new Receiver<SimpleBarProxy>() {
          @Override
          public void onSuccess(SimpleBarProxy response) {
            response = checkSerialization(response);
            assertEquals("A", response.getUserName());
            // Mark the object as deleted
            SimpleBarRequest context = simpleBarRequest();
            response = context.edit(response);
            response.setFindFails(true);
            response.setUserName("B");
            context.persistAndReturnSelf().using(response).fire(
                new Receiver<SimpleBarProxy>() {

                  @Override
                  public void onSuccess(SimpleBarProxy response) {
                    response = checkSerialization(response);
                    // The last-known state should be returned
                    assertNotNull(response);
                    assertEquals("B", response.getUserName());

                    SimpleBarRequest context = simpleBarRequest();
                    // Ensure attempts to mutate deleted objects don't blow up
                    response = context.edit(response);
                    response.setUserName("C");

                    // Attempting to use the now-deleted object should fail
                    context.persistAndReturnSelf().using(response).fire(
                        new Receiver<SimpleBarProxy>() {
                          @Override
                          public void onFailure(ServerFailure error) {
                            assertTrue(error.getMessage().contains(
                                "The requested entity is not available on"
                                    + " the server"));
                            finishTestAndReset();
                          }

                          @Override
                          public void onSuccess(SimpleBarProxy response) {
                            response = checkSerialization(response);
                            fail();
                          }
                        });
                  }
                });
          }
        });
  }

  public void testValueObjectCreateSetRetrieveUpdate() {
    delayTestFinish(DELAY_TEST_FINISH);
    SimpleFooRequest req = simpleFooRequest();
    req.findSimpleFooById(1L).fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        SimpleFooRequest req = simpleFooRequest();

        // Create
        final SimpleValueProxy created = req.create(SimpleValueProxy.class);
        created.setNumber(42);
        created.setString("Hello world!");
        created.setSimpleFoo(response);
        // Test cycles in value
        created.setSimpleValue(Arrays.asList(created));

        // Set
        response = req.edit(response);
        response.setSimpleValue(created);

        // Retrieve
        req.persistAndReturnSelf().using(response).with(
            "simpleValue.simpleFoo", "simpleValue.simpleValue").to(
            new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                SimpleValueProxy value = response.getSimpleValue();
                assertEquals(42, value.getNumber());
                assertEquals("Hello world!", value.getString());
                assertSame(response, value.getSimpleFoo());
                assertSame(value, value.getSimpleValue().get(0));

                try {
                  // Require owning object to be editable
                  response.getSimpleValue().setNumber(43);
                  fail("Should have thrown exception");
                } catch (IllegalStateException expected) {
                }

                // Update
                SimpleFooRequest req = simpleFooRequest();
                response = req.edit(response);
                response.getSimpleValue().setNumber(43);
                req.persistAndReturnSelf().using(response).with("simpleValue").to(
                    new Receiver<SimpleFooProxy>() {
                      @Override
                      public void onSuccess(SimpleFooProxy response) {
                        response = checkSerialization(response);
                        assertEquals(43, response.getSimpleValue().getNumber());
                        finishTestAndReset();
                      }
                    }).fire();
              }
            }).fire();
      }
    });
  }

  public void testValueObjectCreateSetRetrieveUpdateViaList() {
    delayTestFinish(DELAY_TEST_FINISH);
    SimpleFooRequest req = simpleFooRequest();
    req.findSimpleFooById(1L).fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        response = checkSerialization(response);
        SimpleFooRequest req = simpleFooRequest();

        // Create
        final SimpleValueProxy created = req.create(SimpleValueProxy.class);
        created.setNumber(42);
        created.setString("Hello world!");
        created.setSimpleFoo(response);

        // Set
        response = req.edit(response);
        response.setSimpleValues(Arrays.asList(created));

        // Retrieve
        req.persistAndReturnSelf().using(response).with("simpleValues").to(
            new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                response = checkSerialization(response);
                SimpleValueProxy value = response.getSimpleValues().get(0);
                assertEquals(42, value.getNumber());

                try {
                  // Require owning object to be editable
                  response.getSimpleValues().get(0).setNumber(43);
                  fail("Should have thrown exception");
                } catch (IllegalStateException expected) {
                }

                // Update
                SimpleFooRequest req = simpleFooRequest();
                response = req.edit(response);
                response.getSimpleValues().get(0).setNumber(43);
                req.persistAndReturnSelf().using(response).with("simpleValues").to(
                    new Receiver<SimpleFooProxy>() {
                      @Override
                      public void onSuccess(SimpleFooProxy response) {
                        response = checkSerialization(response);
                        assertEquals(43,
                            response.getSimpleValues().get(0).getNumber());
                        finishTestAndReset();
                      }
                    }).fire();
              }
            }).fire();
      }
    });
  }

  public void testValueObjectEquality() {
    SimpleFooRequest req = simpleFooRequest();
    SimpleValueProxy a = req.create(SimpleValueProxy.class);
    SimpleValueProxy b = req.create(SimpleValueProxy.class);
    checkEqualityAndHashcode(a, b);

    a.setString("Hello");
    assertFalse(a.equals(b));
    assertFalse(b.equals(a));

    b.setString("Hello");
    checkEqualityAndHashcode(a, b);

    a.setSimpleValue(Collections.singletonList(req.create(SimpleValueProxy.class)));
    assertFalse(a.equals(b));
    assertFalse(b.equals(a));
    b.setSimpleValue(Collections.singletonList(req.create(SimpleValueProxy.class)));
    checkEqualityAndHashcode(a, b);

    a.getSimpleValue().get(0).setNumber(55);
    assertFalse(a.equals(b));
    assertFalse(b.equals(a));
    b.getSimpleValue().get(0).setNumber(55);
    checkEqualityAndHashcode(a, b);
  }

  /**
   * Since a ValueProxy cannot be passed into RequestContext edit, a proxy
   * returned from a service method should be mutable by default.
   */
  public void testValueObjectReturnedFromRequestIsImmutable() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().returnValueProxy().fire(
        new Receiver<SimpleValueProxy>() {
          @Override
          public void onSuccess(SimpleValueProxy a) {
            a = checkSerialization(a);
            try {
              a.setNumber(77);
              fail();
            } catch (IllegalStateException expected) {
            }
            try {
              // Ensure Dates comply with ValueProxy mutation behaviors
              a.getDate().setTime(1);
              fail();
            } catch (IllegalStateException expected) {
            }
            SimpleFooRequest ctx = simpleFooRequest();
            final SimpleValueProxy toCheck = ctx.edit(a);
            toCheck.setNumber(77);
            toCheck.getDate().setTime(1);
            ctx.returnValueProxy().fire(new Receiver<SimpleValueProxy>() {
              @Override
              public void onSuccess(SimpleValueProxy b) {
                b = checkSerialization(b);
                b = simpleFooRequest().edit(b);
                // Now check that same value is equal across contexts
                b.setNumber(77);
                b.setDate(new Date(1));
                checkEqualityAndHashcode(toCheck, b);
                finishTestAndReset();
              }
            });
          }
        });
  }

  public void testValueObjectViolationsOnCreate() {
    delayTestFinish(DELAY_TEST_FINISH);
    SimpleFooRequest req = simpleFooRequest();
    final SimpleValueProxy value = req.create(SimpleValueProxy.class);
    value.setShouldBeNull("Hello world");

    SimpleFooProxy foo = req.create(SimpleFooProxy.class);
    foo.setSimpleValue(value);
    req.echo(foo).fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy response) {
        fail();
      }

      @Override
      public void onViolation(Set<Violation> errors) {
        assertEquals(1, errors.size());
        Violation v = errors.iterator().next();
        assertEquals(value, v.getInvalidProxy());
        assertNull(v.getOriginalProxy());
        assertEquals("shouldBeNull", v.getPath());
        assertNull(v.getProxyId());
        finishTestAndReset();
      }
    });
  }

  public void testValueObjectViolationsOnEdit() {
    delayTestFinish(DELAY_TEST_FINISH);
    simpleFooRequest().returnValueProxy().fire(
        new Receiver<SimpleValueProxy>() {
          @Override
          public void onSuccess(SimpleValueProxy response) {
            final SimpleValueProxy original = checkSerialization(response);
            SimpleFooRequest req = simpleFooRequest();
            final SimpleValueProxy value = req.edit(response);
            value.setShouldBeNull("Hello world");
            SimpleFooProxy foo = req.create(SimpleFooProxy.class);
            foo.setSimpleValue(value);
            req.echo(foo).fire(new Receiver<SimpleFooProxy>() {
              @Override
              public void onSuccess(SimpleFooProxy response) {
                fail();
              }

              @Override
              public void onViolation(Set<Violation> errors) {
                assertEquals(1, errors.size());
                Violation v = errors.iterator().next();
                assertEquals(value, v.getInvalidProxy());
                assertEquals(original, v.getOriginalProxy());
                assertEquals("shouldBeNull", v.getPath());
                assertNull(v.getProxyId());
                finishTestAndReset();
              }
            });
          }
        });
  }

  public void testViolationAbsent() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy newFoo = context.create(SimpleFooProxy.class);
    final Request<Void> fooReq = context.persist().using(newFoo);

    newFoo = context.edit(newFoo);
    newFoo.setUserName("Amit"); // will not cause violation.

    fooReq.fire(new Receiver<Void>() {
      @Override
      public void onSuccess(Void ignore) {
        finishTestAndReset();
      }
    });
  }

  public void testViolationsOnCreate() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy newFoo = context.create(SimpleFooProxy.class);
    final Request<SimpleFooProxy> create = context.persistAndReturnSelf().using(
        newFoo);
    new FailFixAndRefire<SimpleFooProxy>(newFoo, context, create).doTest();
  }

  public void testViolationsOnCreateVoidReturn() {
    delayTestFinish(DELAY_TEST_FINISH);

    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy newFoo = context.create(SimpleFooProxy.class);
    final Request<Void> create = context.persist().using(newFoo);
    new FailFixAndRefire<Void>(newFoo, context, create).doVoidTest();
  }

  public void testViolationsOnEdit() {
    delayTestFinish(DELAY_TEST_FINISH);

    fooCreationRequest().fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy returned) {
        returned = checkSerialization(returned);
        SimpleFooRequest context = simpleFooRequest();
        Request<SimpleFooProxy> editRequest = context.persistAndReturnSelf().using(
            returned);
        new FailFixAndRefire<SimpleFooProxy>(returned, context, editRequest).doTest();
      }
    });
  }

  public void testViolationsOnEditVoidReturn() {
    delayTestFinish(DELAY_TEST_FINISH);

    fooCreationRequest().fire(new Receiver<SimpleFooProxy>() {
      @Override
      public void onSuccess(SimpleFooProxy returned) {
        returned = checkSerialization(returned);
        SimpleFooRequest context = simpleFooRequest();
        Request<Void> editRequest = context.persist().using(returned);
        new FailFixAndRefire<Void>(returned, context, editRequest).doVoidTest();
      }
    });
  }

  protected SimpleBarRequest simpleBarRequest() {
    return req.simpleBarRequest();
  }

  protected SimpleFooRequest simpleFooRequest() {
    return req.simpleFooRequest();
  }

  private void assertCannotFire(final Request<Long> mutateRequest) {
    try {
      mutateRequest.fire(new Receiver<Long>() {
        @Override
        public void onSuccess(Long response) {
          fail("Should not be called");
        }
      });
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      /* cannot reuse a successful request, mores the pity */
    }
  }

  private Request<SimpleFooProxy> fooCreationRequest() {
    SimpleFooRequest context = simpleFooRequest();
    SimpleFooProxy originalFoo = context.create(SimpleFooProxy.class);
    final Request<SimpleFooProxy> fooReq = context.persistAndReturnSelf().using(
        originalFoo);
    originalFoo = context.edit(originalFoo);
    originalFoo.setUserName("GWT User");
    return fooReq;
  }
}
