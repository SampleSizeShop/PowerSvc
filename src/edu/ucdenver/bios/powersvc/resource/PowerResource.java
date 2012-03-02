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
package edu.ucdenver.bios.powersvc.resource;

import java.util.List;

import org.restlet.resource.Get;
import org.restlet.resource.Post;

import edu.cudenver.bios.power.Power;
import edu.ucdenver.bios.webservice.common.domain.StudyDesign;

public interface PowerResource
{
    @Post
    public SimplePojo modPojo(SimplePojo pojo);
    
    @Get
    public SimplePojo getPojo(SimplePojo pojo);
    
	/**
	 * Calculate power for the specified study design
	 * 
	 * @param studyDesign study design object
	 * @return List of power objects for the study design
	 */
	@Post
	public List<Power> getPower(StudyDesign studyDesign);

	/**
	 * Calculate the total sample size for the specified study design.
	 * 
	 * @param studyDesign study design object
	 * @return List of power objects for the study design.  These will contain the total sample size
	 */
	@Post
	public List<Power> getSampleSize(StudyDesign studyDesign);

	/**
	 * Calculate the detectable difference for the specified study design.
	 * 
	 * @param studyDesign study design object
	 * @return List of power objects for the study design.  These will contain the detectable difference
	 */
	@Post
	public List<Power> getDetectableDifference(StudyDesign studyDesign);
}