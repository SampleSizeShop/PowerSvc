/*
 * Power Service for the GLIMMPSE Software System.  Processes
 * incoming HTTP requests for power, sample size, and detectable
 * difference
 * 
 * Copyright (C) 2010 Regents of the University of Colorado.  
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package edu.cudenver.bios.powersvc.test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;

import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

import edu.cudenver.bios.power.GLMMPower;
import edu.cudenver.bios.power.Power;
import edu.cudenver.bios.power.parameters.GLMMPowerParameters;
import edu.cudenver.bios.powersvc.representation.GLMMPowerListXMLRepresentation;
import edu.cudenver.bios.powersvc.resource.ParameterResourceHelper;

import junit.framework.TestCase;

/**
 * Unit test for building the outgoing representation of power results
 */
public class TestRepresentation extends TestCase
{
	private ArrayList<Power> powerList = new ArrayList<Power>();
	
	private static final String expectedXML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"+
	"<powerList count=\"3\">"+
	"<glmmPower actualPower=\"0.8\" alpha=\"0.05\" betaScale=\"3.0\" "+
	"nominalPower=\"0.8\" sampleSize=\"10\" sigmaScale=\"5.0\" test=\"hlt\"/>"+
	"<glmmPower actualPower=\"0.8\" alpha=\"0.05\" betaScale=\"3.0\" " + 
	"nominalPower=\"0.8\" sampleSize=\"10\" sigmaScale=\"5.0\" test=\"wl\"/>"+
	"<glmmPower actualPower=\"0.8\" alpha=\"0.05\" betaScale=\"3.0\" "+
	"nominalPower=\"0.8\" sampleSize=\"10\" sigmaScale=\"5.0\" test=\"pbt\"/></powerList>";
	
	/**
	 * Create some power results
	 */
    public void setUp()
    {
    	powerList.add(new GLMMPower(GLMMPowerParameters.Test.HOTELLING_LAWLEY_TRACE,0.05,
    			0.8, 0.8, 10, 3, 5));
    	powerList.add(new GLMMPower(GLMMPowerParameters.Test.WILKS_LAMBDA,0.05,
    			0.8, 0.8, 10, 3, 5));
    	powerList.add(new GLMMPower(GLMMPowerParameters.Test.PILLAI_BARTLETT_TRACE,0.05,
    			0.8, 0.8, 10, 3, 5));
    }

    /**
     * Test that the representation matches the expected XML string above.
     */
    public void testGLMMPowerListXMLRepresentation()
    {
        try
        {
        	GLMMPowerListXMLRepresentation rep = new GLMMPowerListXMLRepresentation(powerList);
        	StringWriter sw = new StringWriter();
        	rep.write(sw); 
            assertEquals(sw.toString(), expectedXML);
        }
        catch(Exception e)
        {
            System.out.println("Exception during representation test: " + e.getMessage());
            e.printStackTrace();
            fail();
        }
    }
}
