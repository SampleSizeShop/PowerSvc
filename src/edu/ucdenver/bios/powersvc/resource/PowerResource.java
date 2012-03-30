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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */
package edu.ucdenver.bios.powersvc.resource;

import java.util.ArrayList;
import org.restlet.resource.Post;
import edu.cudenver.bios.power.GLMMPower;
import edu.ucdenver.bios.webservice.common.domain.NamedMatrix;
import edu.ucdenver.bios.webservice.common.domain.PowerResult;
import edu.ucdenver.bios.webservice.common.domain.StudyDesign;

/**
 * Main interface for calculating power, sample size, and
 * detectable difference.
 * @author Sarah Kreidler
 *
 */
public interface PowerResource {
    /**
     * Calculate power for the specified study design.
     *
     * @param studyDesign study design object
     * @return List of power objects for the study design
     */
    @Post
    ArrayList<PowerResult> getPower(StudyDesign studyDesign);

    /**
     * Calculate the total sample size for the specified study design.
     *
     * @param studyDesign study design object
     * @return List of power objects for the study design.  These will contain
     * the total sample size
     */
    @Post
    ArrayList<PowerResult> getSampleSize(StudyDesign studyDesign);

    /**
     * Calculate the detectable difference for the specified study design.
     *
     * @param studyDesign study design object
     * @return List of power objects for the study design.  These will contain
     * the detectable difference.
     */
    @Post
    ArrayList<PowerResult> getDetectableDifference(StudyDesign studyDesign);
    
    /**
     * Get matrices used in the power calculation for a "guided" study design
     */
    @Post
    ArrayList<NamedMatrix> getMatrices(StudyDesign studyDesign);
}
