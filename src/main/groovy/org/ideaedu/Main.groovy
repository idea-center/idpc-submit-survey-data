package org.ideaedu

import idea.data.rest.RESTSurvey

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.HelpFormatter

import org.joda.time.LocalDate

/**
 * The Main class provides a way to test the diagnostic survey/report by generating test data and submitting it to
 * the IDEA REST Server through the REST API. It has some optional command line arguments that control the behavior.
 * The arguments include:
 * <ul>
 * <li>h (host) - the hostname of the IDEA REST Server</li>
 * <li>p (port) - the port that is open on the IDEA REST Server</li>
 * <li>b (basePath) - the base path within the IDEA REST Server</li>
 * <li>sid (srcID) - the survey source ID</li>
 * <li>sgid (srcGroupID) - the survey source group ID</li>
 * <li>iid (institutionID) - the institution ID to use for this survey (which institution this survey is associated with)</li>
 * <li>v (verbose) - provide more output on the command line</li>
 * <li>a (app) - the client application name</li>
 * <li>k (key) - the client application key</li>
 * <li>t (type) - the type of survey to submit (chair, admin, or diag)</li>
 * <li>s (ssl) - use SSL/TLS for connection to the IDEA REST Server</li>
 * <li>eo (extraOpen) - the number of extra open qestions to add to the rater form.</li>
 * <li>es (extraScaled) - the number of extra scaled questions to add to the rater form.</li>
 * <li>c (cip) - the program code this survey is associated with.</li>
 * <li>de (demographics) - the number of demographic sub-groups to use (0, 2-4 are valid).</li>
 * <li>ras (asked) - the number of respondents asked to respond (defaults to 10).</li>
 * <li>ran (answered) - the number of respondents that answered questions (defaults to 10).</li>
 * <li>? (help) - show the usage of this</li>
 * </ul>
 *
 * @author Todd Wallentine todd AT IDEAedu org
 */
public class Main {

	private static final String DEFAULT_SRC_ID = '1'
	private static final String DEFAULT_SRC_GROUP_ID = '2'
	private static final int DEFAULT_INSTITUTION_ID = 1029 // ID_INSTITUTION in Combo for The IDEA Center, 365568 is IDEA Education
	private static final String DEFAULT_HOSTNAME = 'localhost'
	private static final int DEFAULT_PORT = 8091
	private static final String DEFAULT_BASE_PATH = 'v1'
    private static final SurveyType DEFAULT_TYPE = SurveyType.TEACHING_ESSENTIALS
    private static final String DEFAULT_PROTOCOL = 'http'
    private static final String DEFAULT_PROGRAM_CODE = "51.2001"
    private static final int DEFAULT_EXTRA_SCALED_QUESTION_COUNT = 0
    private static final int DEFAULT_EXTRA_OPEN_QUESTION_COUNT = 0
    private static final int DEFAULT_DEMOGRAPHIC_GROUP_COUNT = 0
    private static final int DEFAULT_NUMBER_OF_RESPONDENTS = 10
	private static final int DEFAULT_NUMBER_OF_SURVEYS = 1
    private static final String DEFAULT_APP_NAME = ''
    private static final String DEFAULT_APP_KEY = ''

    private static appName = DEFAULT_APP_NAME
    private static appKey = DEFAULT_APP_KEY
    private static protocol = DEFAULT_PROTOCOL
	private static String hostname = DEFAULT_HOSTNAME
	private static int port = DEFAULT_PORT
	private static String basePath = DEFAULT_BASE_PATH
	private static String srcID = DEFAULT_SRC_ID
	private static String srcGroupID = DEFAULT_SRC_GROUP_ID
	private static int institutionID = DEFAULT_INSTITUTION_ID
	private static type = DEFAULT_TYPE
    private static cipCode = DEFAULT_PROGRAM_CODE
    private static extraScaledQuestionCount = DEFAULT_EXTRA_SCALED_QUESTION_COUNT
    private static extraOpenQuestionCount = DEFAULT_EXTRA_OPEN_QUESTION_COUNT
    private static int demographicGroupCount = DEFAULT_DEMOGRAPHIC_GROUP_COUNT
    private static int numAsked = DEFAULT_NUMBER_OF_RESPONDENTS
    private static int numAnswered = DEFAULT_NUMBER_OF_RESPONDENTS
	private static int numSurveys = DEFAULT_NUMBER_OF_SURVEYS

	private static boolean verboseOutput = false

	private static Random random = new Random() // TODO Should we seed it? -todd 11Jun2013

	static void main(String[] args) {

		/*
		 * TODO Other command line options that might be useful:
		 * 1) data file - contents define the answers to info form and rater form questions
		 * 2) year, term, start/end date, gap analysis flag
		 */
        def parser = new DefaultParser()
        def options = new Options()
        options.addOption(new Option('v', 'verbose', false, 'verbose output'))
        options.addOption(new Option('s', 'ssl', false, 'use SSL (default: false)'))
        options.addOption(new Option('h', 'host', true, 'host name (default: localhost)'))
        options.addOption(new Option('p', 'port', true, 'port number (default: 8091)'))
        options.addOption(new Option('b', 'basePath', true, 'base REST path (default: v1'))
        options.addOption(new Option('sid', 'srcID', true, 'source ID'))
        options.addOption(new Option('sgid', 'srcGroupID', true, 'source Group ID'))
        options.addOption(new Option('iid', 'institutionID', true, 'institution ID'))
        options.addOption(new Option('a', 'app', true, 'client application name'))
        options.addOption(new Option('k', 'key', true, 'client application key'))
        options.addOption(new Option('t', 'type', true, 'survey type'))
        options.addOption(new Option('c', 'cip', true, 'cip code'))
        options.addOption(new Option('ras', 'asked', true, 'number of respondents asked to respond'))
        options.addOption(new Option('ran', 'answered', true, 'number of respondents that responded'))
        options.addOption(new Option('sn', 'surveys', true, 'number of surveys'))
        options.addOption(new Option('de', 'demographics', true, 'demographic groups'))
        options.addOption(new Option('es', 'extraScaled', true, 'extra scaled questions'))
        options.addOption(new Option('eo', 'extraOpen', true, 'extra open questions'))
        options.addOption(new Option('?', 'help', false, 'help'))
        def cmd = parser.parse(options, args)

		if(cmd.hasOption('?')) {
			def formatter = new HelpFormatter()
            formatter.printHelp('idpc-submit-survey-data', options)
			return
		}
		if(cmd.hasOption('v')) {
			verboseOutput = true
		}
        if(cmd.hasOption('s')) {
            protocol = 'https'
        }
		if(cmd.hasOption('h')) {
			hostname = cmd.getOptionValue('h')
		}
		if(cmd.hasOption('p')){
			port = cmd.getOptionValue('p').toInteger()
		}
		if(cmd.hasOption('b')) {
			basePath = cmd.getOptionValue('b')
		}
		if(cmd.hasOption('sid')) {
			srcID = cmd.getOptionValue('sid')
		}
		if(cmd.hasOption('sgid')) {
			srcGroupID = cmd.getOptionValue('sgid')
		}
		if(cmd.hasOption('iid')) {
			institutionID = cmd.getOptionValue('iid').toInteger()
		}
		if(cmd.hasOption('a')) {
			appName = cmd.getOptionValue('a')
		}
		if(cmd.hasOption('k')) {
			appKey = cmd.getOptionValue('k')
		}
		if(cmd.hasOption('t')) {
            def typeString = cmd.getOptionValue('t')
            println "Options have type specified: ${typeString}"
            type = SurveyType.get(typeString)
            if(!type) {
                println "Unable to find an instrument type with that name: ${typeString}"
                return
            }
		}
        if(cmd.hasOption('c')) {
            cipCode = cmd.getOptionValue('c')
        }
        if(cmd.hasOption('ras')) {
            numAsked = cmd.getOptionValue('ras').toInteger()
            numAnswered = numAsked // default 100% response rate
        }
        if(cmd.hasOption('ran')) {
            numAnswered = cmd.getOptionValue('ran').toInteger()
        }
		if (cmd.hasOption('sn')) {
			numSurveys = cmd.getOptionValue('sn').toInteger()
		}
        if(cmd.hasOption('de')) {
            demographicGroupCount = cmd.getOptionValue('de').toInteger()
        }
        if(cmd.hasOption('es')) {
            extraScaledQuestionCount = cmd.getOptionValue('es').toInteger()
        }
        if(cmd.hasOption('eo')) {
            extraOpenQuestionCount = cmd.getOptionValue('eo').toInteger()
        }

		def year = Calendar.instance.get(Calendar.YEAR)
		def term = "Spring"
		def includesGapAnalysis = false

		Date today = new Date()
		Date yesterday = today - 1
		Date creationDate = today - 10 // 10 days ago
		Date infoFormStartDate = today - 9 // 9 days ago
		Date infoFormEndDate = yesterday
		Date raterFormStartDate = today - 5 // 5 days ago
		Date raterFormEndDate = yesterday
		Date courseStartDate = today - 60
		Date courseEndDate = today -30

        def programCode = cipCode

        def builder = new Builder(verboseOutput)
        def dataPortal = new DataPortal(protocol, hostname, port, basePath, appName, appKey, verboseOutput)

        def demographicGroups = dataPortal.getDemographicGroups(demographicGroupCount)
        def demographicGroupIDs
        if(demographicGroups) {
            demographicGroupIDs = demographicGroups.collect { it.id }
        }

        def infoFormQuestionGroups = dataPortal.getQuestionGroups(type.infoFormID)
		def restInfoForm = builder.buildRESTInfoForm(infoFormStartDate, infoFormEndDate, type, infoFormQuestionGroups, null, (type.isSRI ? programCode : null))
		def course
		if(type.isSRI) {
            course = builder.buildRESTCourse((courseStartDate ? new LocalDate(courseStartDate) : null), (courseEndDate ? new LocalDate(courseEndDate) : null))
		}
        def raterFormQuestionGroups = dataPortal.getQuestionGroups(type.raterFormID)
        def restRaterForm = builder.buildRESTRaterForm(raterFormStartDate, raterFormEndDate, numAsked, numAnswered,
            type.raterFormID, raterFormQuestionGroups, extraScaledQuestionCount, extraOpenQuestionCount,
            demographicGroups)
		def start = new Date()

        if(numSurveys > 1) {
            def index = 1
    		1.upto(numSurveys, {
                // TODO: Move this to Builder - builder.buildRESTSurvey(). -todd
    			def restSurvey = new RESTSurvey(srcId: "${srcGroupID}_${index.toString().padLeft(5, '0')}", srcGroupId: srcGroupID, institutionId: institutionID,
    					year: year, term: term, includesGapAnalysis: includesGapAnalysis, creationDate: creationDate,
    					infoForm: restInfoForm, raterForm: restRaterForm, course: course, demographicGroupIds: demographicGroupIDs)
    			dataPortal.submitSurveyData(restSurvey)
    			index++
    		})
    		def stop = new Date()
    		def duration = groovy.time.TimeCategory.minus(
    				stop,
    				start
    		)

    		println "Duration: $duration.hours:$duration.minutes:$duration.seconds"
        } else {
            // TODO: Move this to Builder - builder.buildRESTSurvey(). -todd
            def restSurvey = new RESTSurvey(srcId: srcID, srcGroupId: srcGroupID, institutionId: institutionID,
                    year: year, term: term, includesGapAnalysis: includesGapAnalysis, creationDate: creationDate,
                    infoForm: restInfoForm, raterForm: restRaterForm, course: course, demographicGroupIds: demographicGroupIDs)
            dataPortal.submitSurveyData(restSurvey)
        }
	}
}