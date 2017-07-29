/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ib.position;

import com.ib.api.IBClient;
import org.apache.log4j.Logger;
import java.util.HashMap;

/**
 *
 * @author Siteng Jin
 */
public class PositionManager {
    private static final Logger LOG = Logger.getLogger(PositionManager.class);
    
    private IBClient m_client = null;
    
    private HashMap<Integer, Position> m_positions = null; // Map conid to position
    
    public PositionManager(IBClient client){
        LOG.info("Initializing Position Manager");
        m_client = client;
        m_positions = new HashMap<Integer, Position>();
    }
    
    public void startRequestingPosition(){
        m_client.getSocket().reqPositions();
        LOG.info("Sent reqPositions()");
    }
    
    public synchronized double getPosition(int conid){
        Position pos = m_positions.get(conid);
        return pos == null ? 0.0 : pos.getPos();
    }
    
    public synchronized void updatePosition(Position pos){
        int conid = pos.getContract().conid();
        if(m_positions.containsKey(conid)){
            m_positions.replace(conid, pos);
        } else {
            m_positions.put(conid, pos);
        }
        LOG.info("Updated position:" + m_positions.toString());
    }
}
