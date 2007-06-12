package org.jboss.seam.wiki.core.search;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.store.DirectoryProvider;
import org.hibernate.search.util.ContextHelper;
import org.jboss.seam.Component;
import org.jboss.seam.annotations.Asynchronous;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.log.Log;
import org.jboss.seam.wiki.util.Progress;

import javax.persistence.EntityManager;
import javax.transaction.UserTransaction;

/**
 * Management the Lucene index.
 *
 * @author Christian Bauer
 */
@Name("indexManager")
public class IndexManager {

    @Logger
    static Log log;

    // TODO: Read the Hibernate Seach configuration option instead, when it becomes available as an API
    public int batchSize = 50;

    /**
     * Runs asynchronously and re-indexes the given entity class after purging the index.
     *
     * @param entityClass the class to purge and re-index
     * @param progress a value holder that is continously updated while the asynchronous procedure runs
     */
    @Asynchronous
    public void rebuildIndex(Class entityClass, Progress progress) {
        log.info("asynchronously rebuilding Lucene index for entity: " + entityClass);

        UserTransaction userTx = null;

        try {
            progress.setStatus("Purging index");
            log.debug("deleting indexed documents");
            userTx = (UserTransaction)org.jboss.seam.Component.getInstance("org.jboss.seam.transaction.transaction");
            userTx.begin();

            EntityManager em = (EntityManager) Component.getInstance("entityManager");
            Session session = (Session) em.getDelegate();

            // Delete all documents with "_hibernate_class" term of the selected entity
            DirectoryProvider dirProvider = ContextHelper.getSearchFactory(session).getDirectoryProvider(entityClass);
            IndexReader reader = IndexReader.open(dirProvider.getDirectory());

            // TODO: This is using an internal term of HSearch
            reader.deleteDocuments(new Term("_hibernate_class", entityClass.getName()));
            reader.close();

            // Optimize index
            progress.setStatus("Optimizing index");
            log.debug("optimizing index (merging segments)");
            Search.createFullTextSession(session).getSearchFactory().optimize(entityClass);

            userTx.commit();

            progress.setStatus("Building index");
            log.debug("indexing documents in batches of: " + batchSize);

            // Now re-index with HSearch
            em = (EntityManager) Component.getInstance("entityManager");
            session = (Session) em.getDelegate();
            FullTextSession ftSession = org.hibernate.search.Search.createFullTextSession(session);

            userTx.begin();

            // Use HQL instead of Criteria to eager fetch lazy properties
            ScrollableResults cursor = session.createQuery("select o from " + entityClass.getName() + " o fetch all properties").scroll();

            cursor.last();
            int count = cursor.getRowNumber() + 1;
            log.debug("total documents in database: " + count);

            cursor.first(); // Reset to first result row
            int i = 0;
            while (true) {
                i++;
                Object o = cursor.get(0);
                log.debug("indexing: " + o);
                ftSession.index(o);
                if (i % batchSize == 0) session.clear(); // Clear persistence context for each batch

                progress.setPercentComplete( (100/count) * i);
                log.debug("percent of index update complete: " + progress);

                if (cursor.isLast())
                    break;
                else
                    cursor.next();
            }
            cursor.close();
            userTx.commit();

            progress.setStatus(Progress.COMPLETE);
            log.debug("indexing complete of entity class: " + entityClass);

        } catch (Exception ex) {
            try {
                if (userTx != null) userTx.rollback();
            } catch (Exception rbEx) {
                rbEx.printStackTrace();
            }
            throw new RuntimeException(ex);
        }

    }


}
