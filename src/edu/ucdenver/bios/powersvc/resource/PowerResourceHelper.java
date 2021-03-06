/*
 * Power Service for the GLIMMPSE Software System.  Processes
 * incoming HTTP requests for power, sample size, and detectable
 * difference
 *
 * Copyright (C) 2017 Regents of the University of Colorado.
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import edu.cudenver.bios.matrix.FixedRandomMatrix;
import edu.cudenver.bios.matrix.MatrixUtilities;
import edu.cudenver.bios.matrix.MatrixUtils;
import edu.cudenver.bios.power.GLMMPower;
import edu.cudenver.bios.power.Power;
import edu.cudenver.bios.power.glmm.GLMMPowerConfidenceInterval.ConfidenceIntervalType;
import edu.cudenver.bios.power.glmm.GLMMTestFactory;
import edu.cudenver.bios.power.parameters.GLMMPowerParameters;
import edu.cudenver.bios.utils.Logger;
import edu.ucdenver.bios.powersvc.application.PowerConstants;
import edu.ucdenver.bios.powersvc.application.PowerLogger;
import edu.ucdenver.bios.webservice.common.domain.BetaScale;
import edu.ucdenver.bios.webservice.common.domain.BetweenParticipantFactor;
import edu.ucdenver.bios.webservice.common.domain.Blob2DArray;
import edu.ucdenver.bios.webservice.common.domain.Category;
import edu.ucdenver.bios.webservice.common.domain.ClusterNode;
import edu.ucdenver.bios.webservice.common.domain.ConfidenceInterval;
import edu.ucdenver.bios.webservice.common.domain.ConfidenceIntervalDescription;
import edu.ucdenver.bios.webservice.common.domain.Covariance;
import edu.ucdenver.bios.webservice.common.domain.Hypothesis;
import edu.ucdenver.bios.webservice.common.domain.HypothesisBetweenParticipantMapping;
import edu.ucdenver.bios.webservice.common.domain.HypothesisRepeatedMeasuresMapping;
import edu.ucdenver.bios.webservice.common.domain.NamedMatrix;
import edu.ucdenver.bios.webservice.common.domain.NamedMatrixList;
import edu.ucdenver.bios.webservice.common.domain.NominalPower;
import edu.ucdenver.bios.webservice.common.domain.PowerMethod;
import edu.ucdenver.bios.webservice.common.domain.PowerResult;
import edu.ucdenver.bios.webservice.common.domain.PowerResultList;
import edu.ucdenver.bios.webservice.common.domain.Quantile;
import edu.ucdenver.bios.webservice.common.domain.RelativeGroupSize;
import edu.ucdenver.bios.webservice.common.domain.RepeatedMeasuresNode;
import edu.ucdenver.bios.webservice.common.domain.SampleSize;
import edu.ucdenver.bios.webservice.common.domain.SigmaScale;
import edu.ucdenver.bios.webservice.common.domain.StatisticalTest;
import edu.ucdenver.bios.webservice.common.domain.StudyDesign;
import edu.ucdenver.bios.webservice.common.domain.TypeIError;
import edu.ucdenver.bios.webservice.common.enums.HypothesisTypeEnum;
import edu.ucdenver.bios.webservice.common.enums.PowerMethodEnum;
import edu.ucdenver.bios.webservice.common.enums.SolutionTypeEnum;
import edu.ucdenver.bios.webservice.common.enums.StatisticalTestTypeEnum;
import edu.ucdenver.bios.webservice.common.enums.StudyDesignViewTypeEnum;

import static edu.cudenver.bios.matrix.MatrixUtilities.forceSymmetric;

/**
 * Helper class for conversion to/from domain layer objects
 * @author Sarah Kreidler
 *
 */
public final class PowerResourceHelper {

    /** The logger. */
    private static final Logger LOGGER = Logger.getLogger(PowerLogger.getInstance());

    /** The maximum number of groups we consider reasonable. */
    private static final int MAX_GROUPS = 100;

    /** The maximum number of cases we consider reasonable. */
    private static final int MAX_CASES = 72;

    /**
     * Convert a study design object into a power parameters object
     * TODO: should be removed once modifications to java stats are complete
     *
     * @param studyDesign
     * @return power parameter object for use with JavaStatistics
     */
    public static GLMMPowerParameters studyDesignToPowerParameters(StudyDesign studyDesign)
            throws IllegalArgumentException {
        validate(studyDesign);

        GLMMPowerParameters params = new GLMMPowerParameters();

        /** Build list inputs **/
        // add tests
        if (studyDesign.getStatisticalTestList() != null) {
            for(StatisticalTest test: studyDesign.getStatisticalTestList()) {
                params.addTest(toGLMMTest(test));
            }
        }
        // add alpha values
        if (studyDesign.getAlphaList() != null) {
            for(TypeIError alpha: studyDesign.getAlphaList()) {
                params.addAlpha(alpha.getAlphaValue());
            }
        }
        // add nominal powers
        if (studyDesign.getNominalPowerList() != null) {
            for(NominalPower power: studyDesign.getNominalPowerList()) {
                params.addPower(power.getValue());
            }
        }
        // add per group sample sizes
        if (studyDesign.getSampleSizeList() != null) {
            for(SampleSize size: studyDesign.getSampleSizeList()) {
                params.addSampleSize(size.getValue());
            }
        }
        // add beta scale values
        if (studyDesign.getBetaScaleList() != null) {
            for(BetaScale betaScale: studyDesign.getBetaScaleList()) {
                params.addBetaScale(betaScale.getValue());
            }
        }
        // add sigma scale values
        if (studyDesign.getSigmaScaleList() != null) {
            for(SigmaScale scale: studyDesign.getSigmaScaleList()) {
                params.addSigmaScale(scale.getValue());
            }
        }

        /** Generate and add matrices **/

        // build design matrix
        params.setDesignEssence(designMatrixFromStudyDesign(studyDesign));

        // build beta matrix
        params.setBeta(betaMatrixFromStudyDesign(studyDesign));
        debug("beta set to:", params.getBeta().getCombinedMatrix());

        // build the between subject contrast
        params.setBetweenSubjectContrast(betweenParticipantContrastFromStudyDesign(studyDesign));

        // build the within subject contrast
        params.setWithinSubjectContrast(withinParticipantContrastFromStudyDesign(studyDesign));

        // build theta null matrix
        params.setTheta(thetaNullMatrixFromStudyDesign(studyDesign,
                params.getBetweenSubjectContrast(),
                params.getWithinSubjectContrast()));

        // add matrices for either GLMM(F) or GLMM(F,g) designs
        if (studyDesign.isGaussianCovariate()) {
            RealMatrix sigmaY = sigmaOutcomesMatrixFromStudyDesign(studyDesign);
            RealMatrix sigmaG = sigmaCovariateMatrixFromStudyDesign(studyDesign);
            params.setSigmaOutcome(sigmaY);
            params.setSigmaGaussianRandom(sigmaG);
            params.setSigmaOutcomeGaussianRandom(
                    sigmaOutcomesCovariateMatrixFromStudyDesign(studyDesign,
                            sigmaG, sigmaY));

            // add power methods
            if (studyDesign.getPowerMethodList() != null) {
                for(PowerMethod method: studyDesign.getPowerMethodList()) {
                    params.addPowerMethod(toGLMMPowerMethod(method));
                }
            }
            // add quantiles
            if (studyDesign.getQuantileList() != null) {
                for(Quantile quantile: studyDesign.getQuantileList()) {
                    params.addQuantile(quantile.getValue());
                }
            }
        } else {
            params.setSigmaError(forceSymmetric(sigmaErrorMatrixFromStudyDesign(studyDesign)));
            params.addPowerMethod(GLMMPowerParameters.PowerMethod.CONDITIONAL_POWER);

            // add confidence intervals if specified
            ConfidenceIntervalDescription CIdescr = studyDesign.getConfidenceIntervalDescriptions();
            if (CIdescr != null) {
                params.setAlphaLowerConfidenceLimit(CIdescr.getLowerTailProbability());
                params.setAlphaUpperConfidenceLimit(CIdescr.getUpperTailProbability());
                params.setDesignMatrixRankForEstimates(CIdescr.getRankOfDesignMatrix());
                params.setSampleSizeForEstimates(CIdescr.getSampleSize());
                if (CIdescr.isBetaFixed() && !CIdescr.isSigmaFixed()) {
                    params.setConfidenceIntervalType(ConfidenceIntervalType.BETA_KNOWN_SIGMA_ESTIMATED);
                } else if (!CIdescr.isBetaFixed() && !CIdescr.isSigmaFixed()) {
                    params.setConfidenceIntervalType(ConfidenceIntervalType.BETA_SIGMA_ESTIMATED);
                }
            }
        }

        return params;
    }

    /**
     * Convert a domain layer PowerMethod to a GLMMPowerParameters.PowerMethod.
     * @param method domain layer PowerMethod
     * @return GLMMPowerParameters.PowerMethod
     * @throws IllegalArgumentException on unknown power methods
     */
    private static GLMMPowerParameters.PowerMethod toGLMMPowerMethod(PowerMethod method)
    throws IllegalArgumentException {
        switch(method.getPowerMethodEnum()) {
        case CONDITIONAL:
            return GLMMPowerParameters.PowerMethod.CONDITIONAL_POWER;
        case QUANTILE:
            return GLMMPowerParameters.PowerMethod.QUANTILE_POWER;
        case UNCONDITIONAL:
            return GLMMPowerParameters.PowerMethod.UNCONDITIONAL_POWER;
        default:
            throw new IllegalArgumentException("unknown power method");
        }
    }

    /**
     * Convert a GLMMPowerParameters.PowerMethod to a domain layer PowerMethod.
     * @param method GLMMPowerParameters.PowerMethod
     * @return domain layer PowerMethod
     * @throws IllegalArgumentException on unknown power methods
     */
    private static PowerMethod toPowerMethod(GLMMPowerParameters.PowerMethod method)
    throws IllegalArgumentException {
        switch(method) {
        case CONDITIONAL_POWER:
            return new PowerMethod(PowerMethodEnum.CONDITIONAL);
        case QUANTILE_POWER:
            return new PowerMethod(PowerMethodEnum.QUANTILE);
        case UNCONDITIONAL_POWER:
            return new PowerMethod(PowerMethodEnum.UNCONDITIONAL);
        default:
            throw new IllegalArgumentException("unknown power method");
        }
    }

    /**
     * Convert a domain layer statistical test to a GLMMTest enum
     */
    private static GLMMTestFactory.Test toGLMMTest(StatisticalTest test)
    throws IllegalArgumentException {
        switch(test.getType()) {
        case UNIREP:
            return GLMMTestFactory.Test.UNIREP;
        case UNIREPBOX:
            return GLMMTestFactory.Test.UNIREP_BOX;
        case UNIREPGG:
            return GLMMTestFactory.Test.UNIREP_GEISSER_GREENHOUSE;
        case UNIREPHF:
            return GLMMTestFactory.Test.UNIREP_HUYNH_FELDT;
        case WL:
            return GLMMTestFactory.Test.WILKS_LAMBDA;
        case PBT:
            return GLMMTestFactory.Test.PILLAI_BARTLETT_TRACE;
        case HLT:
            return GLMMTestFactory.Test.HOTELLING_LAWLEY_TRACE;
        default:
            throw new IllegalArgumentException("unknown test");
        }
    }

    /**
     * Convert a domain layer statistical test to a GLMMTest enum
     */
    private static StatisticalTest toStatisticalTest(GLMMTestFactory.Test test)
    throws IllegalArgumentException {
        switch(test) {
        case UNIREP:
            return new StatisticalTest(StatisticalTestTypeEnum.UNIREP);
        case UNIREP_BOX:
            return new StatisticalTest(StatisticalTestTypeEnum.UNIREPBOX);
        case UNIREP_GEISSER_GREENHOUSE:
            return new StatisticalTest(StatisticalTestTypeEnum.UNIREPGG);
        case UNIREP_HUYNH_FELDT:
            return new StatisticalTest(StatisticalTestTypeEnum.UNIREPHF);
        case WILKS_LAMBDA:
            return new StatisticalTest(StatisticalTestTypeEnum.WL);
        case PILLAI_BARTLETT_TRACE:
            return new StatisticalTest(StatisticalTestTypeEnum.PBT);
        case HOTELLING_LAWLEY_TRACE:
            return new StatisticalTest(StatisticalTestTypeEnum.HLT);
        default:
            throw new IllegalArgumentException("unknown test");
        }
    }

    /**
     * Return the design matrix if present, or generate a cell means coded
     * design essence matrix for "guided" study designs
     * @param studyDesign study design object
     * @return design essence matrix
     */
    public static RealMatrix designMatrixFromStudyDesign(StudyDesign studyDesign) {
        if (studyDesign.getViewTypeEnum() == StudyDesignViewTypeEnum.MATRIX_MODE) {
            // matrix based design
            return toRealMatrix(studyDesign.getNamedMatrix(PowerConstants.MATRIX_DESIGN));
        } else {
            /* For Guided study designs, we assume a cell means coding.  Thus, the design matrix is
             * simply an identity matrix with dimension equal to the product of the number of levels
             * of each between subject factor
             * We add additional rows for unequal group sizes
             */
            int totalColumns = 1;
            // calculate the product of the #levels of each between participant factor
            List<BetweenParticipantFactor> factorList = studyDesign.getBetweenParticipantFactorList();
            if (factorList != null) {
                for(BetweenParticipantFactor factor: factorList) {
                    List<Category> categoryList = factor.getCategoryList();
                    if (categoryList != null) {
                        totalColumns *= categoryList.size();
                    }
                }
            }
            int totalRows = 0;
            List<RelativeGroupSize> groupSizeList = studyDesign.getRelativeGroupSizeList();
            if (totalColumns > 1) {
                // we have multiple groups, so check for unequal group sizes
                if (groupSizeList != null) {
                    if (groupSizeList.size() != totalColumns) {
                        // invalid relative group size list
                        throw new IllegalArgumentException("Invalid list of relative group sizes");
                    }
                    for(RelativeGroupSize relativeSize: groupSizeList) {
                        totalRows += relativeSize.getValue();
                    }
                } else {
                    totalRows = totalColumns;
                }
            } else {
                // only 1 column, so the X matrix must be 1x1
                totalRows = 1;
            }
            RealMatrix designEssenceMatrix = null;
            // make sure we didn't produce bad dimensions
            if (totalRows <=0 || totalColumns <= 0) {
                throw new IllegalArgumentException("Unable to produce a valid design matrix");
            }
            // now build the actual matrix
            if (totalRows == totalColumns) {
                // equal group sizes, so just a basic cell means coding (i.e. identity)
                designEssenceMatrix =
                org.apache.commons.math3.linear.MatrixUtils.createRealIdentityMatrix(totalRows);
            } else {
                // unequal group sizes, so start with a zero matrix
                designEssenceMatrix =
                    MatrixUtils.getRealMatrixWithFilledValue(totalRows, totalColumns, 0);
                // now set 1's in the appropriate places
                int col = 0;
                int row = 0;
                for(RelativeGroupSize relativeSize: groupSizeList) {
                    for(int counter = 0; counter < relativeSize.getValue(); counter++) {
                        designEssenceMatrix.setEntry(row, col, 1);
                        row++;
                    }
                    col++;
                }
            }
            return designEssenceMatrix;
        }
    }

    /**
     * Create a fixed/random beta matrix from the study design.
     * @param studyDesign study design object
     * @return fixed/random beta matrix
     */
    public static FixedRandomMatrix betaMatrixFromStudyDesign(StudyDesign studyDesign) {
        double[][] betaFixedData = null;
        double[][] betaRandomData = null;

        NamedMatrix betaFixed =
            studyDesign.getNamedMatrix(PowerConstants.MATRIX_BETA);
        NamedMatrix betaRandom =
            studyDesign.getNamedMatrix(PowerConstants.MATRIX_BETA_RANDOM);

        // get the beta information from the study design matrices
        if (betaFixed != null) {
            betaFixedData = betaFixed.getData().getData();
        }
        if (betaRandom != null) {
            betaRandomData = betaRandom.getData().getData();
        }

        // for guided mode designs, we need to adjust for clustering
        if (studyDesign.getViewTypeEnum() == StudyDesignViewTypeEnum.GUIDED_MODE) {
            List<ClusterNode> clusterNodeList = studyDesign.getClusteringTree();
            if (clusterNodeList != null && clusterNodeList.size() > 0) {
                int totalColumns = 1;
                for(ClusterNode node: clusterNodeList) {
                    totalColumns *= node.getGroupSize();
                }

                // direct product the beta matrix with a matrix of ones to
                // generate the proper dimensions for a cluster sample
                RealMatrix oneMatrix = MatrixUtils.getRealMatrixWithFilledValue(1, totalColumns, 1);

                RealMatrix betaFixedMatrix = new Array2DRowRealMatrix(betaFixedData);
                betaFixedMatrix = MatrixUtils.getKroneckerProduct(oneMatrix, betaFixedMatrix);
                // reset the data
                betaFixedData = betaFixedMatrix.getData();

                // now repeat for the beta random matrix
                if (betaRandom != null) {
                    RealMatrix betaRandomMatrix = new Array2DRowRealMatrix(betaRandomData);
                    betaRandomMatrix = MatrixUtils.getKroneckerProduct(oneMatrix, betaRandomMatrix);
                    // reset the data
                    betaRandomData = betaRandomMatrix.getData();
                }
            }
        }

        FixedRandomMatrix betaFixedRandom =
            new FixedRandomMatrix(betaFixedData, betaRandomData, false);
        return betaFixedRandom;
    }

    /**
     * Create a fixed/random between participant contrast (C matrix)
     *  from the study design.
     * @param studyDesign study design object
     * @return fixed/random C matrix
     */
    public static FixedRandomMatrix betweenParticipantContrastFromStudyDesign(StudyDesign studyDesign) {
        if (studyDesign.getViewTypeEnum() == StudyDesignViewTypeEnum.MATRIX_MODE) {
            // matrix based design
            NamedMatrix cFixed =
                studyDesign.getNamedMatrix(PowerConstants.MATRIX_BETWEEN_CONTRAST);
            NamedMatrix cRandom =
                studyDesign.getNamedMatrix(PowerConstants.MATRIX_BETWEEN_CONTRAST_RANDOM);

            return
            new FixedRandomMatrix((cFixed != null ? cFixed.getData().getData() : null),
                    (cRandom != null ? cRandom.getData().getData() : null),
                    true);
        } else {
            // Guided design
            Set<Hypothesis> hypothesisSet = studyDesign.getHypothesis();
            if (hypothesisSet != null) {
                // only consider the primary hypothesis at present (i.e. the first one)
                Hypothesis hypothesis = hypothesisSet.iterator().next();
                if (hypothesis != null) {
                    RealMatrix cFixed = null;
                    RealMatrix cRandom = null;
                    // get the factor of interest
                    List<HypothesisBetweenParticipantMapping> betweenMap =
                        hypothesis.getBetweenParticipantFactorMapList();
                    if (betweenMap != null && betweenMap.size() > 0) {
                        // build the fixed part of the contrast based on the hypothesis of interest
                        switch (hypothesis.getType()) {
                        case MAIN_EFFECT:
                            // between subject factor of interest
                            cFixed = ContrastHelper.mainEffectBetween(betweenMap.get(0).getBetweenParticipantFactor(),
                                    studyDesign.getBetweenParticipantFactorList());
                            break;
                        case INTERACTION:
                            cFixed = ContrastHelper.interactionBetween(betweenMap,
                                    studyDesign.getBetweenParticipantFactorList());
                            break;
                        case TREND:
                            cFixed = ContrastHelper.trendBetween(betweenMap.get(0),
                                    studyDesign.getBetweenParticipantFactorList());
                            break;
                        case MANOVA:
                            // between subject factor of interest
                            cFixed = ContrastHelper.manovaBetween(betweenMap.get(0).getBetweenParticipantFactor(),
                                    studyDesign.getBetweenParticipantFactorList());
                            break;
                        }
                    } else {
                        cFixed = ContrastHelper.grandMeanBetween(
                                studyDesign.getBetweenParticipantFactorList());
                    }

                    // build the random contrast if the design has a baseline covariate
                    if (studyDesign.isGaussianCovariate()) {
                        if (cFixed != null) {
                            cRandom = MatrixUtils.getRealMatrixWithFilledValue(cFixed.getRowDimension(), 1, 0);
                        }
                    }
                    return new FixedRandomMatrix((cFixed != null ? cFixed.getData() : null),
                            (cRandom != null ? cRandom.getData() : null), true);
                }

                // no hypothesis specified
                return null;
            }
        }

        // unknown view type
        return null;
    }



    /**
     * Create the within participant contrast (U matrix)
     *  from the study design.
     * @param studyDesign study design object
     * @return U matrix
     */
    public static RealMatrix withinParticipantContrastFromStudyDesign(StudyDesign studyDesign) {
        if (studyDesign.getViewTypeEnum() == StudyDesignViewTypeEnum.MATRIX_MODE) {
            return toRealMatrix(studyDesign.getNamedMatrix(PowerConstants.MATRIX_WITHIN_CONTRAST));
        } else {
            // Guided design
            Set<Hypothesis> hypothesisSet = studyDesign.getHypothesis();
            if (hypothesisSet != null) {
                // only consider the primary hypothesis at present (i.e. the first one)
                Hypothesis hypothesis = hypothesisSet.iterator().next();
                if (hypothesis != null) {
                    RealMatrix withinContrast = null;
                    if (hypothesis.getType() == HypothesisTypeEnum.MANOVA) {
                        withinContrast = ContrastHelper.manovaWithin(studyDesign.getResponseList());
                    } else {
                        // get the factor of interest
                        List<HypothesisRepeatedMeasuresMapping> withinMap =
                            hypothesis.getRepeatedMeasuresMapTree();
                        if (withinMap != null && withinMap.size() > 0) {
                            // build the fixed part of the contrast based on the hypothesis of interest
                            switch (hypothesis.getType()) {
                            case MAIN_EFFECT:
                                // within subject factor of interest
                                withinContrast = ContrastHelper.mainEffectWithin(withinMap.get(0).getRepeatedMeasuresNode(),
                                        studyDesign.getRepeatedMeasuresTree(), studyDesign.getResponseList());
                                debug(
                                    "withinContrast after calling mainEffectWithin("
                                        + withinMap.get(0).getRepeatedMeasuresNode() + ", "
                                        + studyDesign.getRepeatedMeasuresTree() + ", "
                                        + studyDesign.getResponseList() + ")",
                                    withinContrast
                                );
                                break;

                            case INTERACTION:
                                withinContrast = ContrastHelper.interactionWithin(withinMap,
                                        studyDesign.getRepeatedMeasuresTree(), studyDesign.getResponseList());
                                break;

                            case TREND:
                                withinContrast = ContrastHelper.trendWithin(withinMap.get(0),
                                        studyDesign.getRepeatedMeasuresTree(), studyDesign.getResponseList());
                                debug(
                                    "withinContrast after calling trendWithin("
                                        + withinMap.get(0) + ", "
                                        + studyDesign.getRepeatedMeasuresTree() + ", "
                                        + studyDesign.getResponseList() + ")",
                                    withinContrast
                                );
                                break;
                            }
                        } else {
                            withinContrast = ContrastHelper.grandMeanWithin(
                                    studyDesign.getRepeatedMeasuresTree(), studyDesign.getResponseList());
                        }
                    }

                    // expand rows if clustering is present
                    if (withinContrast != null) {
                        List<ClusterNode> clusterNodeList = studyDesign.getClusteringTree();
                        if (clusterNodeList != null && clusterNodeList.size() > 0) {
                            int totalRows = 1;
                            for(ClusterNode node: clusterNodeList) {
                                totalRows *= node.getGroupSize();
                            }
                            debug("totalRows is " + totalRows);

                            // direct product the U matrix with a matrix of ones to
                            // generate the proper dimensions for a cluster sample
                            RealMatrix oneMatrix =
                                MatrixUtils.getRealMatrixWithFilledValue(totalRows, 1, 1);
                            withinContrast = MatrixUtils.getKroneckerProduct(oneMatrix, withinContrast);

                        }
                    }

                    return withinContrast;
                }

                // no hypothesis specified
                return null;
            }
        }

        // unknown view type
        return null;
    }

    /**
     * Create a sigma error matrix from the study design.
     * @param studyDesign study design object
     * @return sigma error matrix
     */
    private static RealMatrix sigmaErrorMatrixFromStudyDesign(StudyDesign studyDesign) {
        if (studyDesign.getViewTypeEnum() == StudyDesignViewTypeEnum.MATRIX_MODE) {
            return toRealMatrix(studyDesign.getNamedMatrix(PowerConstants.MATRIX_SIGMA_ERROR));
        } else {
            // guided mode, so we need to decode the covariance objects
            // first, allocate a list of matrices to build the overall kronecker covariance
            ArrayList<RealMatrix> kroneckerMatrixList = new ArrayList<RealMatrix>();

            // add covariance information for clustering
            List<ClusterNode> clusterNodeList = studyDesign.getClusteringTree();
            if (clusterNodeList != null) {
                for(ClusterNode clusterNode: clusterNodeList) {
                    int rows = clusterNode.getGroupSize();
                    int columns = rows;
                    double rho = clusterNode.getIntraClusterCorrelation();
                    // build a compound symmetric matrix
                    double[][] data = new double[rows][columns];
                    for(int row = 0; row < rows; row++) {
                        for(int col = row; col < columns; col++) {
                            if (row == col) {
                                data[row][col] = 1;
                            } else {
                                data[row][col] = rho;
                                data[col][row] = rho;
                            }
                        }
                    }
                    // add the matrix to the kronecker product list
                    kroneckerMatrixList.add(new Array2DRowRealMatrix(data));
                }
            }

            // add covariance for repeated measures
            List<RepeatedMeasuresNode> rmNodeList = studyDesign.getRepeatedMeasuresTree();
            if (rmNodeList != null) {
                for(RepeatedMeasuresNode rmNode: rmNodeList) {
                    Covariance covariance = studyDesign.getCovarianceFromSet(rmNode.getDimension());
                    if (covariance != null) {
                        RealMatrix kroneckerMatrix =
                                CovarianceHelper.covarianceToRealMatrix(covariance, rmNode);
                        if (kroneckerMatrix != null) {
                            kroneckerMatrixList.add(kroneckerMatrix);
                        } else {
                            throw new IllegalArgumentException("Invalid covariance information for factor: " +
                                    rmNode.getDimension());
                        }
                    } else {
                        throw new IllegalArgumentException("Missing covariance information for factor: " +
                                rmNode.getDimension());
                    }
                }
            }
            // lastly, we need to add the covariance of responses
            Covariance covariance = studyDesign.getCovarianceFromSet(
                    PowerConstants.RESPONSES_COVARIANCE_LABEL);
            RealMatrix kroneckerMatrix = CovarianceHelper.covarianceToRealMatrix(covariance,
                    studyDesign.getResponseList());
            if (kroneckerMatrix != null) {
                kroneckerMatrixList.add(kroneckerMatrix);
            } else {
                throw new IllegalArgumentException("Invalid covariance information for response variables");
            }

            return MatrixUtils.getKroneckerProduct(kroneckerMatrixList);
        }
    }

    /**
     * Create a sigma outcomes matrix from the study design.
     * @param studyDesign study design object
     * @return sigma outcomes matrix
     */
    public static RealMatrix sigmaOutcomesMatrixFromStudyDesign(StudyDesign studyDesign) {
        if (studyDesign.getViewTypeEnum() == StudyDesignViewTypeEnum.MATRIX_MODE) {
            return toRealMatrix(studyDesign.getNamedMatrix(PowerConstants.MATRIX_SIGMA_OUTCOME));
        } else {
            return sigmaErrorMatrixFromStudyDesign(studyDesign);
        }
    }

    /**
     * Create a sigma outcomes/covariate matrix from the study design.
     * @param studyDesign study design object
     * @return sigma outcomes/covariate matrix
     */
    public static RealMatrix sigmaOutcomesCovariateMatrixFromStudyDesign(
            StudyDesign studyDesign,
            RealMatrix sigmaG, RealMatrix sigmaY) {
        RealMatrix sigmaYG;

        if (studyDesign.getViewTypeEnum() == StudyDesignViewTypeEnum.MATRIX_MODE) {
            sigmaYG = toRealMatrix(studyDesign.getNamedMatrix(PowerConstants.
                    MATRIX_SIGMA_OUTCOME_GAUSSIAN));
        } else {
            sigmaYG = toRealMatrix(studyDesign.getNamedMatrix(PowerConstants.
                    MATRIX_SIGMA_OUTCOME_GAUSSIAN));
            /*
             * In guided mode, sigmaYG is specified as correlation values.  We adjust
             * to make it into a covariance matrix.  We also expand for clustering
             */
            if (sigmaYG != null) {
                /*
                 * Make into a covariance.  We first make sure the other sigma matrices are
                 * of appropriate dimension to allow this
                 */
                if (sigmaG == null || sigmaG.getRowDimension() <= 0 ||
                        sigmaG.getColumnDimension() <= 0) {
                    throw new IllegalArgumentException("Invalid covariance for Gaussian covariate");
                }
                if (sigmaY == null) {
                    throw new IllegalArgumentException("Invalid covariance for outcome: sigmaY is null");
                }
                debug("sigmaY from JSON", sigmaY);
                debug("sigmaYG from JSON", sigmaYG);

                if (sigmaY.getRowDimension() < sigmaYG.getRowDimension()
                        || sigmaY.getColumnDimension() != sigmaY.getRowDimension()) {
                    throw new IllegalArgumentException(
                        "Invalid covariance for outcome: "
                            + "sigmaY is " + sigmaY.getRowDimension() + " x " + sigmaY.getColumnDimension() + ", "
                            + "sigmaYG is " + sigmaYG.getRowDimension() + " x " + sigmaYG.getColumnDimension()
                    );
                }

                // assumes sigmaG is already updated to be a variance
                double varG = sigmaG.getEntry(0, 0);

                for (int row = 0; row < sigmaYG.getRowDimension(); row++) {
                    double corrYG = sigmaYG.getEntry(row, 0);
                    double varY = sigmaY.getEntry(row, row);
                    sigmaYG.setEntry(row, 0, corrYG * Math.sqrt(varG * varY));
                    debug(
                        "== multiplying sigmaYG[" + (row + 1) + ", " + 1 + "] "
                            + "by sqrt(varG * varY) = sqrt(" + varG + " * " + varY + "): "
                            + "result is " + sigmaYG.getEntry(row, 0)
                    );
                }

                debug("sigmaYG prior to accounting for clustering", sigmaYG);

                // calculate cluster size
                List<ClusterNode> clusterNodeList = studyDesign.getClusteringTree();
                if (clusterNodeList != null && clusterNodeList.size() > 0) {
                    int totalRows = 1;
                    for(ClusterNode node: clusterNodeList) {
                        totalRows *= node.getGroupSize();
                    }

                    // kronecker product the sigmaYG matrix with a matrix of ones to
                    // generate the proper dimensions for a cluster sample
                    debug("== account for clustering by Kroneckering with matrix of ones:");

                    RealMatrix oneMatrix = MatrixUtils.getRealMatrixWithFilledValue(totalRows, 1, 1);
                    debug("matrix of ones", oneMatrix);

                    sigmaYG = MatrixUtils.getKroneckerProduct(oneMatrix, sigmaYG);
                    debug("final sigmaYG", sigmaYG);
                }
            }
        }

        return sigmaYG;
    }

    /**
     * Create a sigma covariate matrix from the study design.
     * @param studyDesign study design object
     * @return sigma covariate matrix
     */
    public static RealMatrix sigmaCovariateMatrixFromStudyDesign(StudyDesign studyDesign) {
        if (studyDesign.getViewTypeEnum() == StudyDesignViewTypeEnum.MATRIX_MODE) {
            return toRealMatrix(studyDesign.getNamedMatrix(PowerConstants.MATRIX_SIGMA_GAUSSIAN));
        } else {
            // in Guided Mode, the user specifies this as a standard deviation, so we square it
            RealMatrix sigmaG =
                toRealMatrix(
                        studyDesign.getNamedMatrix(
                                PowerConstants.MATRIX_SIGMA_GAUSSIAN));
            if (sigmaG != null && sigmaG.getRowDimension() > 0 &&
                    sigmaG.getColumnDimension() > 0) {
                double stddev = sigmaG.getEntry(0, 0);
                sigmaG.setEntry(0, 0, stddev * stddev);
            }
            return sigmaG;
        }
    }

    /**
     * Create a null hypothesis matrix from the study design.
     * @param studyDesign study design object
     * @return theta null matrix
     */
    public static RealMatrix thetaNullMatrixFromStudyDesign(StudyDesign studyDesign,
            FixedRandomMatrix C, RealMatrix U) {
        if (studyDesign.getViewTypeEnum() == StudyDesignViewTypeEnum.MATRIX_MODE) {
            return toRealMatrix(studyDesign.getNamedMatrix(PowerConstants.MATRIX_THETA_NULL));
        } else {
            RealMatrix thetaNull =
                toRealMatrix(studyDesign.getNamedMatrix(PowerConstants.MATRIX_THETA_NULL));
            if (thetaNull == null) {
                if (C != null && C.getFixedMatrix() != null && U != null) {
                    int rows = C.getFixedMatrix().getRowDimension();
                    int columns = U.getColumnDimension();
                    thetaNull = MatrixUtils.getRealMatrixWithFilledValue(rows, columns, 0);
                }
            }
            return thetaNull;
        }
    }

    /**
     * Create the list of matrices generated by the specified study design
     * @param studyDesign study design object
     * @return list of matrices used to compute power
     */
    public static NamedMatrixList namedMatrixListFromStudyDesign(StudyDesign studyDesign) {
        if (studyDesign == null) {
            return null;
        }
        // allocate a result list
        NamedMatrixList matrixList = new NamedMatrixList();
        // parse the study design into matrices
        // build design matrix
        NamedMatrix X = toNamedMatrix(designMatrixFromStudyDesign(studyDesign),
                PowerConstants.MATRIX_DESIGN);
        if (X != null) matrixList.add(X);
        // build beta matrix
        FixedRandomMatrix beta = betaMatrixFromStudyDesign(studyDesign);
        if (beta != null) {
            matrixList.add(toNamedMatrix(beta.getFixedMatrix(), PowerConstants.MATRIX_BETA));
            if (studyDesign.isGaussianCovariate()) {
                matrixList.add(toNamedMatrix(beta.getRandomMatrix(),
                        PowerConstants.MATRIX_BETA_RANDOM));
            }
        }
        // build the between subject contrast
        FixedRandomMatrix C = betweenParticipantContrastFromStudyDesign(studyDesign);
        if (C != null) {
            matrixList.add(toNamedMatrix(C.getFixedMatrix(),
                    PowerConstants.MATRIX_BETWEEN_CONTRAST));
            if (studyDesign.isGaussianCovariate()) {
                matrixList.add(toNamedMatrix(C.getRandomMatrix(),
                        PowerConstants.MATRIX_BETWEEN_CONTRAST_RANDOM));
            }
        }

        // build the within subject contrast
        RealMatrix U = withinParticipantContrastFromStudyDesign(studyDesign);
        if (U != null) {
            matrixList.add(toNamedMatrix(U, PowerConstants.MATRIX_WITHIN_CONTRAST));
        }
        // build theta null matrix
        NamedMatrix thetaNull = toNamedMatrix(thetaNullMatrixFromStudyDesign(studyDesign, C, U),
                PowerConstants.MATRIX_THETA_NULL);
        matrixList.add(thetaNull);

        // add matrices for either GLMM(F) or GLMM(F,g) designs
        if (studyDesign.isGaussianCovariate()) {
            NamedMatrix sigmaY =
                toNamedMatrix(sigmaOutcomesMatrixFromStudyDesign(studyDesign),
                        PowerConstants.MATRIX_SIGMA_OUTCOME);
            if (sigmaY != null) matrixList.add(sigmaY);

            NamedMatrix sigmaG =
                toNamedMatrix(sigmaCovariateMatrixFromStudyDesign(studyDesign),
                        PowerConstants.MATRIX_SIGMA_GAUSSIAN);
            if (sigmaG != null) matrixList.add(sigmaG);

            NamedMatrix sigmaYG =
                toNamedMatrix(sigmaOutcomesCovariateMatrixFromStudyDesign(studyDesign,
                        toRealMatrix(sigmaG), toRealMatrix(sigmaY)),
                        PowerConstants.MATRIX_SIGMA_OUTCOME_GAUSSIAN);
            if (sigmaYG != null) matrixList.add(sigmaYG);
        } else {
            NamedMatrix sigmaE =
                toNamedMatrix(sigmaErrorMatrixFromStudyDesign(studyDesign),
                        PowerConstants.MATRIX_SIGMA_ERROR);
            if (sigmaE != null) matrixList.add(sigmaE);
        }
        return matrixList;
    }

    /**
     * This method takes Named Matrix and converts it into a RealMatrix and
     * returns a Real Matrix.
     *
     * @param namedMatrix
     *            The matrix is a input matrix of type NamedMatrix which is to
     *            be converted to type RealMatrix.
     * @return RealMatrix Returns a RealMatrix which is obtained by converting
     *         the input matrix to a RealMatrix.
     */
    public static RealMatrix toRealMatrix(final NamedMatrix namedMatrix) {
        RealMatrix realMatrix = null;
        if (namedMatrix != null) {
            Blob2DArray blob = namedMatrix.getData();
            if (blob != null) {
                realMatrix = new Array2DRowRealMatrix(blob.getData());
            }
        }
        return realMatrix;
    }

    /**
     * This method takes a Real Matrix and converts it into a Named Matrix and
     * returns that Named Matrix.
     *
     * @param matrix
     *            The matrix is a input matrix of type RealMatrix and is to be
     *            converted to a NamedMatrix.
     * @param name
     *            the name is a String, which is to be assigned to named matrix.
     * @return namedMatrix Returns a NamedMatrix which is obtained by converting
     *         the input matrix to NamedMatrix
     */
    public static NamedMatrix toNamedMatrix(final RealMatrix matrix, final String name) {
        if (matrix == null || name == null || name.isEmpty()) {
            LOGGER.error("failed to create NamedMatrix object name=[" + (name != null ? name : "NULL")+ "]");
            return null;
        }
        NamedMatrix namedMatrix = new NamedMatrix();
        namedMatrix.setDataFromArray(matrix.getData());
        namedMatrix.setName(name);
        namedMatrix.setColumns(matrix.getColumnDimension());
        namedMatrix.setRows(matrix.getRowDimension());
        return namedMatrix;
    }

    /**
     * Convert a list of GLMMPower objects to a list of PowerResult objects
     * @param powerList GLMMPower object list
     * @return list of PowerResult objects
     */
    public static PowerResultList toPowerResultList(List<Power> powerList) {
        PowerResultList resultList = new PowerResultList();
        for(Power power: powerList)
        {
            resultList.add(toPowerResult((GLMMPower) power));
        }
        return resultList;
    }

    /**
     * Convert a GLMMPower object to PowerResult objects
     * @param glmmPower GLMMPower object
     * @return PowerResult object
     */
    public static PowerResult toPowerResult(GLMMPower glmmPower) {
        Quantile quantile = null;
        if (!Double.isNaN(glmmPower.getQuantile())) {
            quantile = new Quantile(glmmPower.getQuantile());
        }

        PowerResult powerResult = new PowerResult(
                toStatisticalTest(glmmPower.getTest()),
                new TypeIError(glmmPower.getAlpha()),
                new NominalPower(glmmPower.getNominalPower()),
                glmmPower.getActualPower(),
                glmmPower.getTotalSampleSize(),
                new BetaScale(glmmPower.getBetaScale()),
                new SigmaScale(glmmPower.getSigmaScale()),
                toPowerMethod(glmmPower.getPowerMethod()),
                quantile,
                toConfidenceInterval(glmmPower.getConfidenceInterval())
        );

        // powerResult.setErrorCode(glmmPower.getErrorCode()); // TODO: convert enum
        powerResult.setErrorMessage(glmmPower.getErrorMessage());

        return powerResult;
    }

    /**
     * Convert a JavaStatistics confidence interval into a domain layer
     * confidence interval
     * @param ci
     * @return domain layer confidence interval object
     */
    private static ConfidenceInterval toConfidenceInterval(edu.cudenver.bios.utils.ConfidenceInterval ci) {
        if (ci == null) {
            return null;
        } else {
            return new ConfidenceInterval(ci.getLowerLimit(), ci.getUpperLimit(),
                    ci.getAlphaLower(), ci.getAlphaUpper());
        }
    }

    /**
     * Perform some input validation on a study design.
     *
     * @param studyDesign The study design.
     *
     * @throws IllegalArgumentException if the study design does not pass
     *                                  some input validity tests.
     */
    private static void validate(StudyDesign studyDesign) {
        // All repeatedMeasuresTree node dimensions must be distinct.
        Set<String> dimensions = new HashSet<String>();
        List<RepeatedMeasuresNode> rmNodes = studyDesign.getRepeatedMeasuresTree();
        if (rmNodes != null) {
            for (RepeatedMeasuresNode rmNode: rmNodes) {
                String dimension = rmNode.getDimension();
                if (dimensions.contains(dimension)) {
                    throw new IllegalArgumentException("Duplicate repeated measures dimension '" + dimension + "'");
                }
                dimensions.add(dimension);
            }
        }

        // All covariance node names must be distinct.
        Set<String> names = new HashSet<String>();
        Set<Covariance> covariances = studyDesign.getCovariance();
        if (covariances != null) {
            for (Covariance covariance: covariances) {
                String name = covariance.getName();
                if (names.contains(name)) {
                    throw new IllegalArgumentException("Duplicate covariance name '" + name + "'");
                }
                names.add(name);
            }
        }

        // The number of groups must be "reasonable".
        validateNumberOfGroups(studyDesign);

        // The number of cases must be "reasonable".
        validateNumberOfCases(studyDesign);
    }

    /**
     * See if the number of groups included in the request defined by
     * a study design is reasonable. "Reasonable" is subjective: it
     * means no greater than the constant MAX_GROUPS.
     *
     * @param studyDesign The study design.
     *
     * @throws IllegalArgumentException if not.
     */
    private static void validateNumberOfGroups(StudyDesign studyDesign) {
        int nGroups = 1;

        List<BetweenParticipantFactor> factorList = studyDesign.getBetweenParticipantFactorList();
        if (factorList != null) {
            for (BetweenParticipantFactor factor: factorList) {
                List<Category> categoryList = factor.getCategoryList();
                if (categoryList != null) {
                    nGroups *= categoryList.size();
                }
            }
        }

        if (nGroups > MAX_GROUPS) {
            StringBuilder sb = new StringBuilder();

            sb.append("To better manage the load on our server, we ask that you limit your request to no more than ")
              .append(MAX_GROUPS)
              .append(" groups.");
            sb.append("<br>");
            sb.append("Your request currently includes ")
               .append(pretty(nGroups))
               .append(" groups (");
            boolean first = true;
            for (BetweenParticipantFactor factor: factorList) {
                List<Category> categoryList = factor.getCategoryList();
                if (categoryList != null) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(" \u00d7 ");
                    }
                    sb.append(categoryList.size());
                }
            }
            sb.append("):");

            sb.append("<ul>");

            for (BetweenParticipantFactor factor: factorList) {
                List<Category> categoryList = factor.getCategoryList();
                if (categoryList != null) {
                    sb.append("<li>")
                      .append("predictor '" + factor.getPredictorName() + "' has " + categoryList.size() + " values")
                      .append("</li>");
                }
            }

            sb.append("</ul>");

            throw new IllegalArgumentException(sb.toString());
        }
    }

    /**
     * See if the number of cases included in the request defined by
     * a study design is reasonable. "Reasonable" is subjective: it
     * means no greater than the constant MAX_CASES.
     *
     * @param studyDesign The study design.
     *
     * @throws IllegalArgumentException if not.
     */
    private static void validateNumberOfCases(StudyDesign studyDesign) {
        SolutionTypeEnum solutionType = studyDesign.getSolutionTypeEnum();
        if (solutionType == null) {
            throw new IllegalArgumentException("Internal error 93.");
        }

        int nUnits;
        switch (solutionType) {
        case POWER:
            nUnits = size(studyDesign.getSampleSizeList());
            break;
        case SAMPLE_SIZE:
            nUnits = size(studyDesign.getNominalPowerList());
            break;
        default:
            nUnits = 0;
            break;
        }

        int nStatisticalTests = size(studyDesign.getStatisticalTestList());
        int nAlphas           = size(studyDesign.getAlphaList());
        int nBetaScales       = size(studyDesign.getBetaScaleList());
        int nSigmaScales      = size(studyDesign.getSigmaScaleList());

        List<PowerMethod> powerMethodList = studyDesign.getPowerMethodList();
        int nPowerMethods =
            (hasPowerMethod(powerMethodList, PowerMethodEnum.UNCONDITIONAL) ? 1 : 0)
                + (hasPowerMethod(powerMethodList, PowerMethodEnum.QUANTILE) ? size(studyDesign.getQuantileList()) : 0);

        // TODO: is integer overflow a serious consideration here?
        int nCases =
              weight(nUnits)
            * weight(nStatisticalTests)
            * weight(nAlphas)
            * weight(nBetaScales)
            * weight(nSigmaScales)
            * weight(nPowerMethods)
        ;

        if (nCases > MAX_CASES) {
            StringBuilder sb = new StringBuilder();

            sb.append("To better manage the load on our server, we ask that you limit your request to no more than ")
              .append(MAX_CASES)
              .append(" cases.");
            sb.append("<br>");
            sb.append("Your request currently includes ")
               .append(pretty(nCases))
               .append(" cases (");
            boolean first = true;
            if (nUnits > 0) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" \u00d7 ");
                }
                sb.append(nUnits);
            }
            if (nStatisticalTests > 0) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" \u00d7 ");
                }
                sb.append(nStatisticalTests);
            }
            if (nAlphas > 0) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" \u00d7 ");
                }
                sb.append(nAlphas);
            }
            if (nBetaScales > 0) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" \u00d7 ");
                }
                sb.append(nBetaScales);
            }
            if (nSigmaScales > 0) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" \u00d7 ");
                }
                sb.append(nSigmaScales);
            }
            if (nPowerMethods > 0) {
                if (first) {
                    first = false;
                } else {
                    sb.append(" \u00d7 ");
                }
                sb.append(nPowerMethods);
            }
            sb.append("):");

            sb.append("<ul>");

            if (nUnits > 0) {
                sb.append("<li>")
                  .append(
                    solutionType == SolutionTypeEnum.POWER
                        ? combination(nUnits, "Group Size", "Group Sizes")
                        : combination(nUnits, "Desired Power", "Desired Powers")
                   )
                  .append("</li>");
            }

            if (nStatisticalTests > 0) {
                sb.append("<li>")
                  .append(combination(nStatisticalTests, "Statistical Test", "Statistical Tests"))
                  .append("</li>");
            }

            if (nAlphas > 0) {
                sb.append("<li>")
                  .append(combination(nAlphas, "Type I Error Rate", "Type I Error Rates"))
                  .append("</li>");
            }

            if (nBetaScales > 0) {
                sb.append("<li>")
                  .append(combination(nBetaScales, "Scale Factor For Means", "Scale Factors for Means"))
                  .append("</li>");
            }

            if (nSigmaScales > 0) {
                sb.append("<li>")
                  .append(combination(nSigmaScales, "Scale Factor for Variability", "Scale Factors for Variability"))
                  .append("</li>");
            }

            if (nPowerMethods > 0) {
                sb.append("<li>")
                  .append(combination(nPowerMethods, "Power Method", "Power Methods"))
                  .append("</li>");
            }

            sb.append("</ul>");

            throw new IllegalArgumentException(sb.toString());
        }
    }

    /**
     * Combine a number with a noun, to produce, for example,
     * "1 apple" or "2 apples".
     *
     * @param n  The number.
     * @param s1 The noun to use if the number is the number 1.
     * @param s  The noun to use otherwise.
     *
     * @return The combination of the number and the noun.
     */
    private static String combination(int n, String s1, String s) {
        return n == 1 ? "1 " + s1 : n + " " + s;
    }

    /**
     * See if a power method list contains a particular power method.
     *
     * @param powerMethodList The power method list.
     * @param powerMethodEnum The particular power method.
     *
     * @return True if it does, else false.
     */
    private static boolean hasPowerMethod(List<PowerMethod> powerMethodList, PowerMethodEnum powerMethodEnum) {
        if (powerMethodList == null) {
            return false;
        }

        for (PowerMethod powerMethod: powerMethodList) {
            if (powerMethod.getPowerMethodEnum() == powerMethodEnum) {
                return true;
            }
        }

        return false;
    }

    /**
     * Return the size of a (possibly null) list.
     *
     * @param The list.
     *
     * @return The size of the list, or zero if null.
     */
    private static int size(List<?> list) {
        return list != null ? list.size() : 0;
    }

    /**
     * Return the multiplicative effect of a factor that may be absent.
     * If the factor is present, its multiplicative effect is its value.
     * If it is absent, its multiplicative effect is the number 1; that
     * is, it has no effect.
     *
     * @param n The value of a factor, or zero if the factor is absent.
     *
     * @return The multiplicative effect of the factor.
     */
    private static int weight(int n) {
        return n != 0 ? n : 1;
    }

    /**
     * A convenience method for DEBUG logging of a message.
     *
     * @param message The message.
     */
    private static void debug(Object message) {
        LOGGER.debug(message);
    }

    /**
     * A convenience method for DEBUG logging of a matrix
     * with a label.
     *
     * @param label      The label.
     * @param realMatrix The matrix.
     */
    private static void debug(String label, RealMatrix realMatrix) {
        LOGGER.debug(MatrixUtilities.logMessageSupplier(label, realMatrix));
    }

    /**
     * Compute a string representation of an integer, with commas.
     * (This method belongs in a utility class somewhere.)
     *
     * @param n The integer.
     *
     * @return The string representation.
     */
    private static String pretty(int n) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(n);
    }
}
