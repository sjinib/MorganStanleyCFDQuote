/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import com.ib.api.IBClient;
import com.ib.client.EReader;
import com.ib.client.EReaderSignal;
import com.ib.morganstanleyquote.MorganStanleyQuoteTest;
import org.apache.log4j.Logger;

/**
 *
 * @author Siteng Jin
 */
public class TestQuotes {
    public static final Logger LOG = Logger.getLogger(TestQuotes.class);
            
    private static String HOST = "127.0.0.1";
    private static int PORT = 7496;
    public static int CLIENTID = 0;
    
    public static void main(String[] args) {
        LOG.info("Starting Morgan Stanley Quote Test");
        
        final IBClient m_client = IBClient.getInstance();
        final EReaderSignal m_signal = m_client.getSignal();
        //! [connect]
        m_client.getSocket().eConnect(HOST, PORT, CLIENTID);
        //! [connect]
        //! [ereader]
        final EReader reader = new EReader(m_client.getSocket(), m_signal);
        reader.start();
        new Thread() {
            public void run() {
                while (m_client.getSocket().isConnected()) {
                    m_signal.waitForSignal();
                    try {
                        reader.processMsgs();
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }
        }.start();
        
        try{
            while(m_client.getCurrentOrderId() == -1){
                Thread.sleep(200);
            }
            
            m_client.start();
            
            Thread.sleep(10000000);
            
        } catch (Exception e){
            LOG.error(e.getMessage(), e);
        }
    }
}
