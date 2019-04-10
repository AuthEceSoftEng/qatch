package miltos.diploma.integrationtests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import miltos.diploma.calibration.BenchmarkAnalyzer;
import miltos.diploma.evaluation.EvaluationResultsExporter;
import miltos.diploma.evaluation.Project;
import miltos.diploma.evaluation.ProjectCharacteristicsEvaluator;
import miltos.diploma.evaluation.ProjectEvaluator;
import miltos.diploma.qualitymodel.*;
import miltos.diploma.toolkit.*;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.*;
import java.nio.file.FileSystems;

@Category(IntegrationTest.class)
public class IntegrationTests {

    private String qmPath;
    private String resPath;
    private boolean includeInspectRes;
    private boolean staticAnalysis;

    /**
     * Tests the single project evaluation test using the following modules:
     *  - qualitymodel
     *  - toolkit
     *  - evaluation
     *
     * Note: to reduce repository size, the projects being analyzed are kept in an outside folder.
     * Adjust the string path provided in singleProjectEvaluatorTest(<PATH_STRING>) if needed.
     *
     * The asserted value at the end is the expected TQI of the project and will vary depending what the project
     * is and which quality model is used. Be sure to adjust the parameter accordingly.
     */
    @Test
    public void singleProjectEvaluatorTest_Java() throws IOException, CloneNotSupportedException {
        singleProjectEvaluatorTest(
        "../sample-analysis-projects/java/SimpleJava",
        "src/test/resources/Models/java/qualityModel_java.xml");
    }

    @Test
    public void singleProjectEvaluatorTest_CSharp() throws IOException, CloneNotSupportedException {
        singleProjectEvaluatorTest(
        "../sample-analysis-projects/csharp/SimpleCSharp",
        "src/main/resources/Models/csharp/qualityModel_csharp.xml");
    }

    private void singleProjectEvaluatorTest(String projectLocation, String qmLocation)
            throws CloneNotSupportedException, IOException {
        System.out.println("******************************  Project Evaluator *******************************");
        System.out.println();

        //Extract necessary tools if not already extracted
        extractResources();
        //Receive the appropriate configuration from the user through terminal
        getUserInputs(projectLocation, qmLocation, "src/test/output", false, true);

        /*
         * Step 0 : Load the desired Quality Model
         */
        System.out.println("**************** STEP 0: Quality Model Loader ************************");
        System.out.println("*");
        System.out.println("* Loading the desired Quality Model...");
        System.out.println("* Please wait...");
        System.out.println("*");

        //Instantiate the Quality Model importer
        QualityModelLoader qmImporter = new QualityModelLoader(qmLocation);

        //Load the desired quality model
        QualityModel qualityModel = qmImporter.importQualityModel();

        System.out.println("* Quality Model successfully loaded..!");

        /*
         * Step 1: Create the Project object that simulates the desired project
         */

        System.out.println("\n**************** STEP 1: Project Loader ******************************");
        System.out.println("*");
        System.out.println("* Loading the desired project...");
        System.out.println("* Please wait...");
        System.out.println("*");

        //Get the directory of the project
        File projectDir = new File(projectLocation);

        //Create a Project object to store the results of the static analysis and the evaluation of this project...
        Project project = new Project();

        //Set the absolute path and the name of the project
        project.setPath(projectLocation);
        project.setName(projectDir.getName());

        System.out.println("* Project Name : " + project.getName());
        System.out.println("* Project Path : " + project.getPath());
        System.out.println("*");
        System.out.println("* Project successfully loaded..!");

        /*
         * Step 2: Analyze the desired project against the selected properties
         */

        if(staticAnalysis){

            //Check if the results directory exists and if not create it. Clear it's contents as well.
            checkCreateClearDirectory(BenchmarkAnalyzer.SINGLE_PROJ_RESULT_PATH);

            //Print some messages...
            System.out.println("\n**************** STEP 2: Project Analyzer ****************************");
            System.out.println("*");
            System.out.println("* Analyzing the desired project");
            System.out.println("* Please wait...");
            System.out.println("*");

            //Instantiate the available single project analyzers of the system ...
            PMDAnalyzer pmd = new PMDAnalyzer();
            CKJMAnalyzer ckjm = new CKJMAnalyzer();

            //Analyze the project against the desired properties of each tool supported by the system...
            pmd.analyze(projectLocation, BenchmarkAnalyzer.SINGLE_PROJ_RESULT_PATH+"/"+project.getName(), qualityModel.getProperties());
            ckjm.analyze(projectLocation, BenchmarkAnalyzer.SINGLE_PROJ_RESULT_PATH+"/"+project.getName(), qualityModel.getProperties());

            //Print some messages to the user
            System.out.println("* The analysis is finished");
            System.out.println("* You can find the results at : " + BenchmarkAnalyzer.SINGLE_PROJ_RESULT_PATH);
            System.out.println();
        }

        /*
         * Step 3: Import the results of the static analysis tools
         */

        System.out.println("\n**************** STEP 3: Results Importer ****************************");
        System.out.println("*");
        System.out.println("* Importing the results of the analysis...");
        System.out.println("* Please wait...");
        System.out.println("*");

        //Create a simple PMD and CKJM Result Importers
        PMDResultsImporter pmdImporter = new PMDResultsImporter();
        CKJMResultsImporter ckjmImporter = new CKJMResultsImporter();

        //Get the directory with the results of the analysis
        File resultsDir = new File(BenchmarkAnalyzer.SINGLE_PROJ_RESULT_PATH+"/"+project.getName());
        File[] results = resultsDir.listFiles();

        //For each result file found in the directory do...
        assert results != null;
        for(File resultFile : results){

            //Check if it is a ckjm result file
            if(!resultFile.getName().contains("ckjm")){

                //Parse the issues and add them to the IssueSet Vector of the Project object
                project.addIssueSet(pmdImporter.parseIssues(resultFile.getAbsolutePath()));

            }else{

                //Parse the metrics of the project and add them to the MetricSet field of the Project object
                project.setMetrics(ckjmImporter.parseMetrics(resultFile.getAbsolutePath()));
            }
        }

        // Print some informative messages to the console
        System.out.println("*");
        System.out.println("* The results of the static analysis are successfully imported ");

        /*
         * Step 4 : Aggregate the static analysis results of the desired project
         */

        System.out.println("\n**************** STEP 4: Aggregation Process *************************");

        //Print some messages
        System.out.println("*");
        System.out.println("* Aggregating the results of the project...");
        System.out.println("* I.e. Calculating the normalized values of its properties...");
        System.out.println("* Please wait...");
        System.out.println("*");

        //Clone the properties of the quality model to the properties of the certain project
        for(int i = 0; i < qualityModel.getProperties().size(); i++){
            //Clone the property and add it to the PropertySet of the current project
            Property p = (Property) qualityModel.getProperties().get(i).clone();
            project.addProperty(p);
        }

        //Create an empty PMDAggregator and CKJMAggregator
        PMDAggregator pmd = new PMDAggregator();
        CKJMAggregator ckjm = new CKJMAggregator();

        //Aggregate all the analysis results
        pmd.aggregate(project);
        ckjm.aggregate(project);

        //Normalize their values
        for(int i = 0; i < project.getProperties().size(); i++){
            Property property =  project.getProperties().get(i);
            property.getMeasure().calculateNormValue();
        }

        System.out.println("*");
        System.out.println("* Aggregation process finished..!");

        /*
         * STEP 5 : Evaluate all the benchmark projects against their thresholds.
         */

        System.out.println("\n**************** STEP 5: Properties Evaluation ***********************");
        System.out.println("*");
        System.out.println("* Evaluating the project's properties against the calculated thresholds...");
        System.out.println("* This will take a while...");
        System.out.println("*");

        //Create a single project property evaluator
        ProjectEvaluator evaluator = new ProjectEvaluator();

        //Evaluate all its properties
        evaluator.evaluateProjectProperties(project);

        System.out.println("*");
        System.out.println("* The project's properties successfully evaluated..!");

        /*
         * Step 6 : Evaluate the project's characteristics
         */

        System.out.println("\n**************** STEP 6: Characteristics Evaluation ******************");
        System.out.println("*");
        System.out.println("* Evaluating the project's characteristics based on the eval values of its properties...");
        System.out.println("* This will take a while...");
        System.out.println("*");

        //Clone the quality model characteristics inside the project
        //For each quality model's characteristic do...
        for(int i = 0; i < qualityModel.getCharacteristics().size(); i++){
            //Clone the characteristic and add it to the CharacteristicSet of the current project
            Characteristic c = (Characteristic) qualityModel.getCharacteristics().get(i).clone();
            project.getCharacteristics().addCharacteristic(c);
        }

        //Create a single project property evaluator
        ProjectCharacteristicsEvaluator charEvaluator = new ProjectCharacteristicsEvaluator();

        //Evaluate the project's characteristics
        charEvaluator.evaluateProjectCharacteristics(project);

        System.out.println("*");
        System.out.println("* The project's characteristics successfully evaluated..!");

        /*
         * Step 7 : Calculate the TQI of the project
         */

        System.out.println("\n**************** STEP 7: TQI Calculation *****************************");
        System.out.println("*");
        System.out.println("* Calgculating the TQI of the project ...");
        System.out.println("* This will take a while...");
        System.out.println("*");

        //Copy the TQI object of the QM to the tqi field of this project
        project.setTqi((Tqi)qualityModel.getTqi().clone());

        //Calculate the project's TQI
        project.calculateTQI();

        System.out.println("*");
        System.out.println("* The TQI of the project successfully evaluated..!");

        /*
         * Step 8 : Export the project's data and properties in a json file
         */

        System.out.println("\n**************** STEP 8: Exporting Evaluation Results ****************");
        System.out.println("*");
        System.out.println("* Exporting the results of the project evaluation...");
        System.out.println("* This will take a while...");
        System.out.println("*");

        //Clear Issues and metrics for more lightweight solution
        //TODO: Remove this ... For debugging purposes only
        if(!includeInspectRes){
            project.clearIssuesAndMetrics();
        }

        EvaluationResultsExporter.exportProjectToJson(project, new File(resPath + "/" + project.getName() + "_evalResults.json").getAbsolutePath());

        System.out.println("* Results successfully exported..!");
        System.out.println("* You can find the results at : " + new File(resPath).getAbsolutePath());

        /*
         * Step 9 : Export the results to the predefined path as well
         */

        checkCreateClearDirectory(EvaluationResultsExporter.SINGLE_PROJ_RESULT_PATH);

        //Export the results
        EvaluationResultsExporter.exportProjectToJson(project, new File(EvaluationResultsExporter.SINGLE_PROJ_RESULT_PATH + "/" + project.getName() + "_evalResults.json").getAbsolutePath());
        System.out.println("* You can find the results at : " + new File(EvaluationResultsExporter.SINGLE_PROJ_RESULT_PATH).getAbsolutePath() + " as well..!");

        /*
         * (Test Step) Step 10: Assert TQI is its expected value
         */
        File evalResults = new File(System.getProperty("user.dir") + "/Results/Evaluation/SingleProjectResults/" + project.getName() + "_evalResults.json" );
        JsonParser parser = new JsonParser();
        JsonObject data = (JsonObject) parser.parse(new FileReader(evalResults));
        double eval = data.getAsJsonObject("tqi").get("eval").getAsDouble();
        Assert.assertEquals (0.6284682895481202, eval, 0.001);
    }


    /**
     * A method that checks the predefined directory structure, creates the
     * directory tree if it doesn't exists and clears it's contents for the
     * new analysis (optional).
     */
    private void checkCreateClearDirectory(String path){

        File dir = new File(path);

        //Check if the directory exists
        if(!dir.isDirectory() || !dir.exists()) dir.mkdirs();

        //Clear previous results
        try {
            FileUtils.cleanDirectory(dir);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Automatically extract necessary scripts and tools for analaysis run.
     */
    private void extractResources() {
        //Set filepath for resources depending if run from jar or in IDE
        String buildLoc, pmd_buildLoc, rulesetsLoc, toolsLoc;
        File rootDirectory = new File(FileSystems.getDefault().getPath(".").toAbsolutePath().toString());

        String resourcesLoc = "src/test/resources/";
        buildLoc = resourcesLoc + "build.xml";
        pmd_buildLoc = resourcesLoc + "pmd_build.xml";
        rulesetsLoc = resourcesLoc + "Rulesets";
        toolsLoc = resourcesLoc + "tools";

        File buildXml = new File(buildLoc);
        File pmd_buildXml = new File(pmd_buildLoc);
        File rulesetsFolder = new File(rulesetsLoc);
        File toolsFolder = new File(toolsLoc);

        try {
            FileUtils.copyFileToDirectory(buildXml, rootDirectory);
            FileUtils.copyFileToDirectory(pmd_buildXml, rootDirectory);
            FileUtils.copyDirectoryToDirectory(rulesetsFolder, rootDirectory);
            FileUtils.copyDirectoryToDirectory(toolsFolder, rootDirectory);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getUserInputs(
            String projectPath,
            String qmPath,
            String outputPath,
            boolean inspectionResults,
            boolean staticAnalysis){

        File dir = new File(projectPath);
        boolean exists = false;
        while(!exists) {
            if(dir.exists() && dir.isDirectory()){

                DirectoryScanner scanner = new DirectoryScanner();
                scanner.setIncludes(new String[]{"**/*.java"});
                scanner.setBasedir(dir.getAbsolutePath());
                scanner.setCaseSensitive(false);
                scanner.scan();
                String[] javaFiles = scanner.getIncludedFiles();

                scanner.setIncludes(new String[]{"**/*.class"});
                scanner.setBasedir(dir.getAbsolutePath());
                scanner.setCaseSensitive(false);
                scanner.scan();
                String[] classFiles = scanner.getIncludedFiles();

                scanner.setIncludes(new String[]{"**/*.jar"});
                scanner.setBasedir(dir.getAbsolutePath());
                scanner.setCaseSensitive(false);
                scanner.scan();
                String[] jarFiles = scanner.getIncludedFiles();

                if(javaFiles.length == 0 && classFiles.length == 0 && jarFiles.length == 0){
                    System.out.println("There are no java, class, or jar files inside the desired directory!");
                }else{
                    exists = true;
                }
            }else{
                System.out.println("The desired directory doesn't exist..!");
            }
        }

        File qmXMLFile = new File(qmPath);
        if(!qmXMLFile.exists() || !qmXMLFile.isFile()){
            throw new RuntimeException("The desired file doesn't exist..!");
        }else if(!qmXMLFile.getName().contains(".xml")){
            throw new RuntimeException("The desired file is not an XML file..!");
        }else{
            this.qmPath = qmXMLFile.getAbsolutePath();
        }

        File resDirPath = new File(outputPath);
        if(resDirPath.exists() && resDirPath.isDirectory()){
            this.resPath = resDirPath.getAbsolutePath();
        }else {
            resDirPath.mkdir();
            this.resPath = resDirPath.getAbsolutePath();
        }

        if(inspectionResults){ this.includeInspectRes = true; }
        else { this.includeInspectRes = false; }

        if(staticAnalysis){ this.staticAnalysis = true; }
        else { throw new RuntimeException("integration test must have new analysis check as configuration"); }

    }
}
