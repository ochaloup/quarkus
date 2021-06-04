/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package io.quarkus.narayana.observers;

import java.io.Serializable;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.BeforeDestroyed;
import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.spi.Context;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionScoped;
import javax.transaction.Transactional;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * @author <a href="https://about.me/lairdnelson"
 *         target="_parent">Laird Nelson</a>
 */
@ApplicationScoped
public class TransactionScopedLifecycleEventsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TransactionScopedLifecycleEventsTest.TransactionalScopedBean.class));

    private static boolean initializedObserved, beforeDestroyedObserved, destroyedObserved;

    @Inject
    private TransactionManager tm;

    @Inject
    private TransactionScopedLifecycleEventsTest self;

    @Inject
    private TransactionalScopedBean bean;

    @BeforeEach
    public void init() {
        initializedObserved = false;
        beforeDestroyedObserved = false;
        destroyedObserved = false;
    }

    @TransactionScoped
    static class TransactionalScopedBean implements Serializable {
        static final long serialVersionUID = 42L;

        void doSomething() {
        }
    }

    @Transactional
    void useTransactionScopedBean() {
        bean.doSomething();
    }

    void transactionScopeActivated(@Observes @Initialized(TransactionScoped.class) final Object initializedEvent,
            final BeanManager beanManager) throws SystemException {
        Assertions.assertNotNull(initializedEvent);
        Assertions.assertTrue(initializedEvent instanceof Transaction);
        Assertions.assertNotNull(beanManager);
        Assertions.assertNotNull(this.tm);
        final Transaction transaction = this.tm.getTransaction();
        Assertions.assertNotNull(transaction);
        Assertions.assertEquals(Status.STATUS_ACTIVE, transaction.getStatus());
        final Context transactionContext = beanManager.getContext(TransactionScoped.class);
        Assertions.assertNotNull(transactionContext);
        Assertions.assertTrue(transactionContext.isActive());
        initializedObserved = true;
    }

    void transactionScopeDectivated(@Observes @Destroyed(TransactionScoped.class) final Object destroyedEvent,
            final BeanManager beanManager) throws SystemException {
        Assertions.assertNotNull(destroyedEvent);
        // assertTrue(destroyedEvent instanceof Transaction);
        Assertions.assertNotNull(beanManager);
        Assertions.assertNotNull(this.tm);
        final Transaction transaction = this.tm.getTransaction();
        Assertions.assertNotNull(transaction);
        Assertions.assertNotEquals(Status.STATUS_ACTIVE, transaction.getStatus());
        try {
            beanManager.getContext(TransactionScoped.class);
            Assertions.fail();
        } catch (final ContextNotActiveException expected) {

        }
        destroyedObserved = true;
    }

    void transactionScopeBeforeDectivated(@Observes @BeforeDestroyed(TransactionScoped.class) final Object beforeDestroyedEvent,
            final BeanManager beanManager) throws SystemException {
        Assertions.assertNotNull(beforeDestroyedEvent);
        Assertions.assertTrue(beforeDestroyedEvent instanceof Transaction);
        Assertions.assertNotNull(beanManager);
        Assertions.assertNotNull(this.tm);
        Assertions.assertNotNull(this.tm.getTransaction());
        final Context transactionContext = beanManager.getContext(TransactionScoped.class);
        Assertions.assertNotNull(transactionContext);
        Assertions.assertTrue(transactionContext.isActive());
        Assertions.assertTrue(beforeDestroyedEvent instanceof Transaction);
        beforeDestroyedObserved = true;
    }

    @Test
    public void testEffects() {
        // bean.doSomething();
        useTransactionScopedBean();
        Assertions.assertTrue(initializedObserved, "Expected observed @Initialized(TransactionScoped.class)");
        Assertions.assertTrue(beforeDestroyedObserved, "Expected observed @BeforeDestroyed(TransactionScoped.class)");
        Assertions.assertTrue(destroyedObserved, "Expected observed @Destroyed(TransactionScoped.class)");
    }

}
