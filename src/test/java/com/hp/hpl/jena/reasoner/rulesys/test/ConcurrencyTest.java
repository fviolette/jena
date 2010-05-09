/******************************************************************
 * File:        ConcurrencyTest.java
 * Created by:  Dave Reynolds
 * Created on:  8 May 2010
 * 
 * (c) Copyright 2010, Epimorphics Limited
 * $Id: ConcurrencyTest.java,v 1.2 2010-05-09 09:41:31 der Exp $
 *****************************************************************/

package com.hp.hpl.jena.reasoner.rulesys.test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.shared.JenaException;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.util.PrintUtil;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

import junit.framework.Assert;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test for deadlock and concurrency problems in rule engines.
 * 
 * <p>Test inspired by suggestions from Timm Linder</p>
 * 
 * @author <a href="mailto:der@hplb.hpl.hp.com">Dave Reynolds</a>
 * @version $Revision: 1.2 $
 */
public class ConcurrencyTest  extends TestCase {

    // For routine jena tests we do minimal exercise here, otherwise too slow
    // If problems crop up then switch to full tests
    final static boolean FULL_TEST = false;
    
    // Number of class instances to create in the model under test
    final static int MODEL_SIZE = FULL_TEST ? 100 : 10;
    
    // Number of threads to create in the tests
    final static int NUM_THREADS = FULL_TEST ? 50 : 20;
    
    // Length of time to run the threads under test, in ms
    final static int TEST_LENGTH = FULL_TEST ? 3000 : 20;
    
    // Number of times to run the test cycle
    final static int NUM_RUNS = FULL_TEST ? 30 : 3;
    
    /**
     * Boilerplate for junit
     */ 
    public ConcurrencyTest( String name ) {
        super( name ); 
    }
    
    /**
     * Boilerplate for junit.
     * This is its own test suite
     */
    public static TestSuite suite() {
        return new TestSuite( ConcurrencyTest.class ); 
    }  
    
    private interface ModelCreator {
        public OntModel createModel();
    }

    private void runConcurrencyTest(ModelCreator modelCreator, String runId) throws InterruptedException  {
        try {
            for(int i = 0; i < NUM_RUNS; ++i) {
                doTestConcurrency(modelCreator.createModel());
            }
        } catch (JenaException e ) {
            assertTrue(e.getMessage(), false);
        }
    }

    private void doTestConcurrency(final OntModel model) throws InterruptedException {
        // initialize the model
        final String NS = PrintUtil.egNS;
        
        model.enterCriticalSection(Lock.WRITE);
        final OntClass Top = model.createClass(NS + "Top");
        for (int i = 0; i < MODEL_SIZE; i++) {
            OntClass C = model.createClass(NS + "C" + i);
            Top.addSubClass(C);
            model.createIndividual(NS + "i" + i, C);
        }
        model.leaveCriticalSection();

        class QueryExecutingRunnable implements Runnable {
            @SuppressWarnings("unchecked")
            public void run() {
                // Keep this thread running until the specified duration has expired
                long runStartedAt = System.currentTimeMillis();
                while(System.currentTimeMillis() - runStartedAt < TEST_LENGTH)
                {
                    Thread.yield();
                    
                    model.enterCriticalSection(Lock.READ);
                    try {
                        // Iterate over all statements
                        StmtIterator it = model.listStatements();
                        while(it.hasNext()) it.nextStatement();
                        it.close();
                        
                        // Check number of instances of Top class
                        int count = 0;
                        ExtendedIterator<OntResource> ei = (ExtendedIterator<OntResource>) Top.listInstances();
                        while (ei.hasNext()) {
                            ei.next();
                            count++;
                        }
                        ei.close();
                        if (MODEL_SIZE != count) {
                            if (FULL_TEST) System.err.println("Failure - found " + count + " instance, expected " + MODEL_SIZE);
                            throw new JenaException("Failure - found " + count + " instance, expected " + MODEL_SIZE);
                        }
                    }
                    finally {   model.leaveCriticalSection(); }
                }
            }
        }
        
        // Start the threads
        ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
        for(int i = 0; i < NUM_THREADS; ++i) {
            executorService.submit(new QueryExecutingRunnable());
        }
        
        // Wait for threads to finish
        executorService.shutdown(); // this will *not* terminate any threads currently running
        Thread.sleep(TEST_LENGTH + 50);
        
        // Possibly in deadlock, wait a little longer to be sure
        for(int i = 0; i < 50 && !executorService.isTerminated(); i++) {
            Thread.sleep(100);
        }
        
        if(!executorService.isTerminated()) {
            // Check for deadlock
            ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
            long[] ids = tmx.findDeadlockedThreads();
            if (ids != null) {
                ThreadInfo[] infos = tmx.getThreadInfo(ids, true, true);
                
                System.err.println("*** Deadlocked threads");
                for (ThreadInfo ti : infos) {
                    System.err.println("Thread \"" + ti.getThreadName() + "\" id=" + ti.getThreadId() + " " 
                            + ti.getThreadState().toString());
                    System.err.println("Lock name: " + ti.getLockName() + " owned by \""
                            + ti.getLockOwnerName() + "\" id=" + ti.getLockOwnerId());
                    System.err.println("\nStack trace:");
                    for(StackTraceElement st : ti.getStackTrace())
                        System.err.println("   " + st.getClassName() + "." + st.getMethodName() 
                                + " (" + st.getFileName() + ":" + st.getLineNumber() + ")" );
                    System.err.println();
                }
            }
            Assert.assertTrue("Deadlock detected!", false);
        }
    }
    
    public void testWithOWLMemMicroRuleInfModel() throws InterruptedException {
        runConcurrencyTest(new ModelCreator() { public OntModel createModel() {
            return ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
        }}, "OWL_MEM_MICRO_RULE_INF");
    }
    
}

