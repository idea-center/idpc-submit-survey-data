package org.ideaedu

import idea.data.rest.RESTCourse
import idea.data.rest.RESTSection
import idea.data.rest.RESTForm
import idea.data.rest.RESTQuestion
import idea.data.rest.RESTQuestionGroup
import idea.data.rest.RESTResponse
import idea.data.rest.RESTResponseOption
import idea.data.rest.RESTRespondent
import idea.data.rest.RESTSurvey
import idea.data.rest.RESTSubGroup

import org.apache.hc.client5.http.ClientProtocolException
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpStatus
import org.apache.hc.core5.http.ParseException
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.ssl.TLS
import org.apache.hc.core5.ssl.SSLContextBuilder
import org.apache.hc.core5.ssl.SSLContexts

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.HelpFormatter

import org.joda.time.LocalDate

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

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

    private static enum SURVEY_TYPE {
        DIAGNOSTIC('Diagnostic', ['diag'], 1, 9, true),
        SHORT('Short', [], 1, 10, true),
        DIAGNOSTIC_2016('Diagnostic 2016', ['diag16'], 21, 22, true),
        LEARNING_ESSENTIALS('Learning Essentials', ['learn', 'learning'], 21, 23, true),
        TEACHING_ESSENTIALS('Teaching Essentials', ['teach', 'teaching'], 19, 20, true),
        ADMIN('Administrator', ['admin'], 17, 18, false),
        CHAIR('Chair', [], 13, 14, false),
		ADVISOR('Advisor', [], 24, 25, false),
		ADVISING_STAFF('Advising Staff', ['aa_staff'], 26, 27, false),
		ADVISING_STUDENT('Advising Student', ['aa_student'], 26, 28, false)

        def name
        def aliases = []
        def infoFormID
        def raterFormID
        def isSRI

        private SURVEY_TYPE(name, aliases, infoFormID, raterFormID, isSRI) {
            this.name = name
            this.aliases = aliases
            this.infoFormID = infoFormID
            this.raterFormID = raterFormID
            this.isSRI = isSRI
        }

        static SURVEY_TYPE get(name) {
            def surveyType

            values().each { type ->
                if((type.name.equalsIgnoreCase(name)) || (type.aliases.contains(name))) {
                    surveyType = type
                }
            }

            return surveyType
        }
    }

	private static final String DEFAULT_SRC_ID = '1'
	private static final String DEFAULT_SRC_GROUP_ID = '2'
	private static final int DEFAULT_INSTITUTION_ID = 3019 // ID_INSTITUTION in Combo for The IDEA Center
	private static final String DEFAULT_HOSTNAME = 'localhost'
	private static final int DEFAULT_PORT = 8091
	private static final String DEFAULT_BASE_PATH = 'v1'
    private static final def DEFAULT_AUTH_HEADERS = [ 'X-IDEA-APPNAME': '', 'X-IDEA-KEY': '' ]
    private static final SURVEY_TYPE DEFAULT_TYPE = SURVEY_TYPE.TEACHING_ESSENTIALS
    private static final String DEFAULT_PROTOCOL = 'http'
    private static final String DEFAULT_PROGRAM_CODE = "51.2001"
    private static final int DEFAULT_EXTRA_SCALED_QUESTION_COUNT = 0
    private static final int DEFAULT_EXTRA_OPEN_QUESTION_COUNT = 0
    private static final int DEFAULT_DEMOGRAPHIC_GROUP_COUNT = 0
    private static final int DEFAULT_NUMBER_OF_RESPONDENTS = 10
	private static final int DEFAULT_NUMBER_OF_SURVEYS = 1

    private static def protocol = DEFAULT_PROTOCOL
	private static String hostname = DEFAULT_HOSTNAME
	private static int port = DEFAULT_PORT
	private static String basePath = DEFAULT_BASE_PATH
	private static String srcID = DEFAULT_SRC_ID
	private static String srcGroupID = DEFAULT_SRC_GROUP_ID
	private static int institutionID = DEFAULT_INSTITUTION_ID
	private static def authHeaders = DEFAULT_AUTH_HEADERS
	private static def type = DEFAULT_TYPE
    private static def cipCode = DEFAULT_PROGRAM_CODE
    private static def extraScaledQuestionCount = DEFAULT_EXTRA_SCALED_QUESTION_COUNT
    private static def extraOpenQuestionCount = DEFAULT_EXTRA_OPEN_QUESTION_COUNT
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
			authHeaders['X-IDEA-APPNAME'] = cmd.getOptionValue('a')
		}
		if(cmd.hasOption('k')) {
			authHeaders['X-IDEA-KEY'] = cmd.getOptionValue('k')
		}
		if(cmd.hasOption('t')) {
            def typeString = cmd.getOptionValue('t')
            println "Options have type specified: ${typeString}"
            type = SURVEY_TYPE.get(typeString)
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

		int year = Calendar.instance.get(Calendar.YEAR)
		String term = "Spring"
		boolean includesGapAnalysis = false

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

        def demographicGroups = getDemographicGroups(demographicGroupCount)
        def demographicGroupIDs
        if(demographicGroups) {
            demographicGroupIDs = demographicGroups.collect { it.id }
        }

		def restInfoForm = buildRESTInfoForm(infoFormStartDate, infoFormEndDate, type, null, (type.isSRI ? programCode : null))
		def course
		if(type.isSRI) {
			course = buildRESTCourse(courseStartDate? new LocalDate(courseStartDate):null, courseEndDate? new LocalDate(courseEndDate):null)
		}
        def restRaterForm = buildRESTRaterForm(raterFormStartDate, raterFormEndDate, numAsked, numAnswered,
          type.raterFormID, extraScaledQuestionCount, extraOpenQuestionCount, demographicGroups)
		def start = new Date()

        if(numSurveys > 1) {
            def index = 1
    		1.upto(numSurveys, {
    			def restSurvey = new RESTSurvey(srcId: "${srcGroupID}_${index.toString().padLeft(5, '0')}", srcGroupId: srcGroupID, institutionId: institutionID,
    					year: year, term: term, includesGapAnalysis: includesGapAnalysis, creationDate: creationDate,
    					infoForm: restInfoForm, raterForm: restRaterForm, course: course, demographicGroupIds: demographicGroupIDs)
    			submitSurveyData(restSurvey)
    			index++
    		})
    		def stop = new Date()
    		def duration = groovy.time.TimeCategory.minus(
    				stop,
    				start
    		)

    		println "Duration: $duration.hours:$duration.minutes:$duration.seconds"
        } else {
            def restSurvey = new RESTSurvey(srcId: srcID, srcGroupId: srcGroupID, institutionId: institutionID,
                    year: year, term: term, includesGapAnalysis: includesGapAnalysis, creationDate: creationDate,
                    infoForm: restInfoForm, raterForm: restRaterForm, course: course, demographicGroupIds: demographicGroupIDs)
            submitSurveyData(restSurvey)
        }
	}

	/**
	 * Build an instance of RESTCourse.
	 *
	 * @return A new RESTCourse that can be used in a RESTSurvey.
	 */
	private static buildRESTCourse(startDate, endDate) {
        def section = buildRESTSection(startDate, endDate)
		def restCourse = new RESTCourse(title: 'Intro to IDEA', number: 'IDEA 101', localCode: '0 234 67', days: 'MTWUF', time: '08:00',
                                        srcId: 'courseSrcId', subject: 'course subject', type: 'undergraduate', deliveryMode: 'Face to Face',
										termType: 'semester', startDate: startDate, endDate: endDate, section: section)
		return restCourse
	}

    private static buildRESTSection(startDate, endDate) {
        def restSection = new RESTSection(title: 'Intro to IDEA - Section I', number: 'IDEA 101 - Sec I', srcId: 'sectionSrcId', subject: 'section subject',
                                          deliveryMode: 'Face to Face', startDate: startDate, endDate: endDate, localCode: '9 87 6 4', days: 'MWF', time: '09:00')
        return restSection
    }

	/**
	 * Build an instance of RESTForm that is a rater form that has the given number of respondents (numberAsked),
	 * starts on the date given (startDate) and ends on the date given (endDate).
	 *
	 * @param Date startDate The date that this rater form will open.
	 * @param Date endDate The date that this rater form will close.
	 * @param int numberAsked The number of respondents that are asked to respond to this survey.
     * @param int numberAnswered The number of respondents that answered questions in this survey.
	 * @param raterFormID The ID of the rater/response form.
     * @param extraScaled The number of extra scaled questions to create (default is 0).
	 * @return RESTForm A new RESTForm instance that is populated with test data.
	 */
	private static buildRESTRaterForm(startDate, endDate, numberAsked, numberAnswered, raterFormID,
        extraScaled=DEFAULT_EXTRA_SCALED_QUESTION_COUNT, extraOpen=DEFAULT_EXTRA_OPEN_QUESTION_COUNT,
        demographicGroups) {

		def questionGroups = getQuestionGroups(raterFormID)

		def restRaterForm = new RESTForm(id: raterFormID, numberAsked: numberAsked, startDate: startDate, endDate: endDate)
        def extraQuestionGroups = buildExtraQuestionGroups(extraScaled, extraOpen)
        if(extraQuestionGroups) {
            restRaterForm.customQuestionGroups = extraQuestionGroups
        }
		def respondents = [] as Set
		for(int i = 0; i < numberAnswered; i++) {
            def rater = new RESTRespondent()
            rater.setType("rater")
            rater.subGroup = selectDemographicGroup(demographicGroups)
   		    def responses = buildRESTResponses(questionGroups, extraQuestionGroups)
		    rater.setResponses(responses)
			respondents.add(rater)
		}
		restRaterForm.setRespondents(respondents)

		return restRaterForm
	}

    /**
     * Select a demographic group, at random, from the list of demographic groups provided.
     * This might return null (when no demographic groups given).
     *
     * @param demographicGroups The list of demographic groups to select from.
     * @return A random demographic group (RESTSubGroup) from the list provided; might be null.
     */
    private static selectDemographicGroup(demographicGroups) {
        def demographicGroup

        if(demographicGroups) {
            def random = new Random()
            def i = random.nextInt(demographicGroups.size())
            demographicGroup = demographicGroups[i]
        }

        return demographicGroup
    }

    /**
     * Build a list of extra question groups that can be added to a form.
     *
     * @param extraScaled The number of extra scaled (likert) questions to add. All are added to a single group.
     * @param extraOpen The number of extra open questions to add. All are added to a single group.
     * @return A list of RESTQuestionGroup instances; this might be empty but not null.
     */
    private static buildExtraQuestionGroups(extraScaled=DEFAULT_EXTRA_SCALED_QUESTION_COUNT, extraOpen=DEFAULT_EXTRA_OPEN_QUESTION_COUNT) {
        def extraQuestionGroups = []

        def qGroupNum = 1000
        def qNum = 1000
        if(extraScaled) {
            def responseOptions = [
                new RESTResponseOption(value: 1, description: 'One', abbreviation: '1', isExcluded: false),
                new RESTResponseOption(value: 2, description: 'Two', abbreviation: '2', isExcluded: false),
                new RESTResponseOption(value: 3, description: 'Three', abbreviation: '3', isExcluded: false)
            ]
            def scaledQuestionGroup = new RESTQuestionGroup(type: 'scaled',
                number: qGroupNum,
                title: 'Extra Scaled Question Group',
                message: 'Please answer these questions',
                responseOptions: responseOptions,
                questions: [])
            for(int i = qNum; i < (extraScaled + qNum); i++) {
                scaledQuestionGroup.questions << new RESTQuestion(type: 'scaled', number: i, text: "Scaled Question ${i}")
            }

            extraQuestionGroups << scaledQuestionGroup
            qGroupNum++
            qNum += extraScaled
        }

        if(extraOpen) {
            def openQuestionGroup = new RESTQuestionGroup(type: 'open',
                number: qGroupNum,
                title: 'Extra Open Question Group',
                message: 'Please answer these questions',
                questions: [])
            for(int i = qNum; i < (extraOpen + qNum); i++) {
                openQuestionGroup.questions << new RESTQuestion(type: 'open', number: i, text: "Open Question ${i}")
            }

            extraQuestionGroups << openQuestionGroup
        }

        return extraQuestionGroups
    }

	/**
	 * Build an instance of RESTForm that is an information form that starts on the date given (startDate) and ends on the
	 * date given (endDate).
	 *
	 * @param Date startDate The date that this information form will open.
	 * @param Date endDate The date that this information form will close.
     * @param SURVEY_TYPE The type of the survey this information form will be associated with (Chair, Diagnostic, etc.).
     * @param String disciplineCode The discipline code to use for the given course (if SRI).
     * @param String programCode The program code to use for the given course (if SRI).
	 * @return RESTForm A new RESTForm instance that is populated with test data.
	 */
	private static buildRESTInfoForm(startDate, endDate, type,
        disciplineCode, programCode=DEFAULT_PROGRAM_CODE) {

		def questionGroups = getQuestionGroups(type.infoFormID)

		def title
		if(type == SURVEY_TYPE.DIAGNOSTIC) {
			title = 'Assistant Professor'
		} else if(type == SURVEY_TYPE.SHORT) {
			title = 'Associate Professor'
        } else if(type == SURVEY_TYPE.LEARNING_ESSENTIALS) {
            title = 'Adjunct Professor'
        } else if(type == SURVEY_TYPE.DIAGNOSTIC_2016) {
            title = 'Professor'
        } else if(type == SURVEY_TYPE.TEACHING_ESSENTIALS) {
            title = 'Instructor'
		} else if(type == SURVEY_TYPE.CHAIR) {
			title = 'Chair'
		} else if(type == SURVEY_TYPE.ADMIN) {
			title = 'Vice Provost'
		} else if(type == SURVEY_TYPE.ADVISOR
				|| type == SURVEY_TYPE.ADVISING_STAFF
				|| type == SURVEY_TYPE.ADVISING_STUDENT) {
			title = 'Advisor'
		}

		def restInfoForm = new RESTForm(id: type.infoFormID, numberAsked: 1, startDate: startDate, endDate: endDate,
			disciplineCode: disciplineCode, programCode: programCode)
		def respondents = [] as Set
		def firstName = 'Test'
		def lastName = "Subject${random.nextInt()}"
		def surveySubject = new RESTRespondent(type: 'subject', firstName: firstName, lastName: lastName, title: title,
                                               srcId: 'srcId', email: 'test@ideaedu.org', sex: 'male', role: 'teaching assistant',
                                               appointment: 'tenured', races: ['Hispanic', 'Asian'], employmentStatus: 'full-time' )
		def responses = buildRESTResponses(questionGroups)
		surveySubject.setResponses(responses)
		respondents.add(surveySubject)
		restInfoForm.setRespondents(respondents)

		return restInfoForm
	}

	/**
	 * Build a Set of RESTResponse instances that answer all the questions in the given question groups.
	 *
	 * @param questionGroups The List of RESTQuestionGroup instances to answer.
     * @param customQuestionGroups The List of extra (custom) RESTQuestionGroup instances to answer.
	 * @return The Set of RESTResponse instances that hold answers to the given questions.
	 */
	private static buildRESTResponses(questionGroups, customQuestionGroups=[]) {
		def responses = [] as Set

		questionGroups?.each { questionGroup ->
			questionGroup.questions?.each() { question ->
				int numAnswers = 1

				//println "Question: $question.id, $question.type, $question.text"
				if (question.type == 'multipleChoiceMultipleAnswer') {
					def responseOptions = getResponseOptions(question, questionGroup)
					if (responseOptions) {
						numAnswers = random.nextInt(responseOptions.size)
						println "multipleChoiceMultipleAnswer question detected, choosing $numAnswers responses"
					}
				}

				numAnswers.times {
					def answer = getRandomAnswer(question, questionGroup)
					if (answer != null) {
						def response = new RESTResponse(groupType: 'standard', questionId: question.id, answer: answer)
						responses.add(response)
					}
				}
			}
		}

        customQuestionGroups?.each { questionGroup ->
            questionGroup.questions?.each() { question ->
                def answer = getRandomAnswer(question, questionGroup)
                if(answer != null) {
                    def response = new RESTResponse(groupType: 'custom', groupNumber: questionGroup.number, questionNumber: question.number, answer: answer)
                    responses.add(response)
                }
            }
        }

		return responses
	}

	/**
	 * Get an answer to the given question. This will select a valid response option if it exists (as an answer to a scaled question)
	 * but otherwise it will simply generate a random String (as an answer to an open question).
	 *
	 * Note: To deal with the "optimization" of response options, we need to pass in the RESTQuestionGroup so that
	 * we have access to response options for this question. The optimization was to allow response options to be
	 * defined at the question group level if all response options are the same for a set of questions.
	 *
	 * @param RESTQuestion question The question to create the answer for.
	 * @param RESTQuestionGroup questionGroup The question group this question is associated with.
	 * @return An answer to the question; this might be null.
	 */
	private static getRandomAnswer(question, questionGroup) {
		def answer

		// Priority is given to question response options. If not defined, use the question group response options.
		if(getResponseOptions(question, questionGroup)) {
            answer = getValue(getResponseOptions(question, questionGroup))
		} else {
			answer = "Test Answer ${random.nextInt()}"
		}

		return answer
	}

	private static getResponseOptions(question, questionGroup) {
		// Priority is given to question response options. If not defined, use the question group response options.
		def responseOptions = question.responseOptions

		if (!responseOptions) {
			responseOptions = questionGroup.responseOptions
		}

		return responseOptions
	}

    /**
     * Get a random answer from the given response options. In some cases, we will return null to signal
     * that no response should be provided.
     *
     * @param responseOptions The possible response option to choose form.
     * @return The response option value; or null if no response is selected.
     */
    private static getValue(responseOptions) {
        def value

        if(responseOptions) {
            def index = random.nextInt(responseOptions.size)
            if(index > 0) {
                value = responseOptions[index].value
            } else if(responseOptions[index].isExcluded && random.nextBoolean()) {
                // The 0:Blank response option was selected - sometimes, we provide it and sometimes we don't
                value = responseOptions[index].value
                if(verboseOutput) {
                    println "The response option is 0:Blank:Excluded and we randomly chose to provide response value."
                }
            } else {
                if(verboseOutput) {
                    println "The response option is 0:Blank:Excluded and we randomly chose to provide NO response value."
                }
            }
        }

        return value
    }

	/**
	 * Get the List of RESTQuestionGroup instances that are associated with the given formID. This will query the
	 * REST API.
	 *
	 * @param int formID The form to get the question groups for.
	 * @return The List of RESTQuestionGroup instances that are associated with the given form(formID).
	 */
	private static getQuestionGroups(formID) {
		def questionGroups = []

		if(verboseOutput) {
            println "Retrieving questions for form ${formID}..."
        }

        def resultString = getData("forms/${formID}/questions")
        def json = new JsonSlurper().parseText(resultString)
		json?.data?.each { qg ->
			// TODO Is there a better way to convert it from the Map (qg) to RESTQuestionGroup? -todd 11Jun2013
			def jsonBuilder = new JsonBuilder(qg)
			def qgJSON = jsonBuilder.toString()
			def restQG = RESTQuestionGroup.fromJSON(qgJSON)
			questionGroups.add(restQG)
		}

		if(verboseOutput) {
            println "Retrieved ${questionGroups.size} question groups for form ${formID}."
        }

		return questionGroups
	}

    /**
	 * BASE-442: This is no longer used
	 *
     * Get a program code that matches the given discipline code. This will grab the first one
     * if there is more than one returned.
     *
     * @param disciplineCode The discipline code to use when searching for the program code.
     * @return A program code that matches the given discipline code. This might be null.
     */
	@Deprecated
    private static getProgramCode(disciplineCode) {
        def programCode

        if(disciplineCode) {
            if(verboseOutput) {
                println "Retrieving a program code for discipline code ${disciplineCode}..."
            }

            def resultString = getData("cip?discipline_code=${disciplineCode}")
            def json = new JsonSlurper().parseText(resultString)
            programCode = json?.data?.first().cip_code

            if(verboseOutput) {
                println "Got a program code for discipline code ${disciplineCode}: ${programCode}"
            }
        }

        return programCode
    }

    private static getDemographicGroups(count) {
        def demographicGroups = []

        if(count > 0) {
            // Get the demographic groups from the API
            // Randomly select from the group to fill up the demographic groups with the count specified
            def client = getRESTClient()
            if(verboseOutput) {
                println "Retrieving ${count} demographic groups ..."
            }

            // Get all demographic subgroups from the API
            def resultString = getData("demographic_groups")
            def json = new JsonSlurper().parseText(resultString)
            def subGroups = []
            json?.data?.each { it ->
                def jsonBuilder = new JsonBuilder(it)
                def subGroupJSON = jsonBuilder.toString()
                subGroups << RESTSubGroup.fromJSON(subGroupJSON)
            }

            // Select some of them at random
            for(int numSelected = 0; numSelected < count; numSelected++) {
                def i = random.nextInt(subGroups.size())
                demographicGroups << subGroups[i]
            }

            if(verboseOutput) {
                println "Got ${subGroups.size()} demographic groups and selected ${count} of them for use."
            }
        }

        return demographicGroups
    }

	/**
	 * Submit the survey data to the REST API.
	 *
	 * @param RESTSurvey restSurvey The survey data to submit to the REST API.
	 */
	private static submitSurveyData(restSurvey) {

		def json = restSurvey.toJSON()
		if(verboseOutput) {
            println "JSON: ${json}"
        }

		try {
            def startTime = new Date().time

            def response = postData("services/survey", json)

            def endTime = new Date().time
            def runTime = endTime - startTime

			if(verboseOutput) {
                println "Status: ${response.status}"
            }
			println "${response.data} -> ${runTime}ms"
		} catch (ex) {
			println 'Caught an exception while submitting the survey data:'
			println "Status: ${ex.response.status}"
			println "Status-Line: ${ex.response.statusLine}"
			println "Content-type: ${ex.response.contentType}"
			println "Data: ${ex.response.data}"
		}
	}

    /**
     * Perform an HTTP GET using the given path and return the response.
     *
     * @param path The relative path for the request; this is combined with the configured hostname, port, etc..
     * @return The result from the HTTP GET request in the form of a String.
     */
    private static getData(path) {

        def result // TODO: Set this to some reasonable default

        if(path) {
            def fullPath = "${protocol}://${hostname}:${port}/${basePath}/${path}"
            if(verboseOutput) {
                println "Making a REST call on GET ${fullPath}"
            }

            def defaultHeaders = [
                new BasicHeader("X-IDEA-APPNAME", authHeaders['X-IDEA-APPNAME']),
                new BasicHeader("X-IDEA-KEY", authHeaders['X-IDEA-KEY'])
            ]

            def sslcontext = SSLContexts.custom()
                .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                .build()
            def sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslcontext)
                .setTlsVersions(TLS.V_1_2)
                .build()
            def connectionMgr = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build()
            try (def httpclient = HttpClients.custom().setConnectionManager(connectionMgr).setDefaultHeaders(defaultHeaders).build()) {
                def httpGet = new HttpGet(fullPath)

                def responseHandler = new HttpClientResponseHandler<String>() {
                    @Override
                    public String handleResponse(final ClassicHttpResponse response) throws IOException {
                        def status = response.getCode()
                        if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                            def entity = response.getEntity()

                            try {
                                return entity != null ? EntityUtils.toString(entity) : null
                            } catch (final ParseException ex) {
                                throw new ClientProtocolException(ex)
                            }
                        } else {
                            throw new ClientProtocolException("Unexpected response status: ${status}")
                        }                        
                    }
                }

                result = httpclient.execute(httpGet, responseHandler)
            }

            if(verboseOutput) {
                println "Completed a REST call to GET ${fullPath}"
            }
        }

        return result
    }

    /**
     * Perform an HTTP POST using the given path and body and return the response.
     *
     * @param path The relative path for the request; this is combined with the configured hostname, port, etc..
     * @param body The body to submit in the HTTP POST.
     * @return The result from the HTTP POST request in the form of a String.
     */
    private static postData(path, body) {
        def result

        if(path) {
            def fullPath = "${protocol}://${hostname}:${port}/${basePath}/${path}"
            if(verboseOutput) {
                println "Making a REST call on POST ${fullPath}"
            }

            def defaultHeaders = [
                new BasicHeader("X-IDEA-APPNAME", authHeaders['X-IDEA-APPNAME']),
                new BasicHeader("X-IDEA-KEY", authHeaders['X-IDEA-KEY'])
            ]

            def sslcontext = SSLContexts.custom()
                .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                .build()
            def sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslcontext)
                .setTlsVersions(TLS.V_1_2)
                .build()
            def connectionMgr = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build()
            try (def httpclient = HttpClients.custom().setConnectionManager(connectionMgr).setDefaultHeaders(defaultHeaders).build()) {
                def httpPost = new HttpPost(fullPath)
                def bodyEntity = new StringEntity(body, ContentType.APPLICATION_JSON)
                httpPost.setEntity(bodyEntity)

                def responseHandler = new HttpClientResponseHandler<String>() {
                    @Override
                    public String handleResponse(final ClassicHttpResponse response) throws IOException {
                        def status = response.getCode()
                        if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
                            def entity = response.getEntity()

                            try {
                                return entity != null ? EntityUtils.toString(entity) : null
                            } catch (final ParseException ex) {
                                throw new ClientProtocolException(ex)
                            }
                        } else {
                            throw new ClientProtocolException("Unexpected response status: ${status}")
                        }                        
                    }
                }

                result = httpclient.execute(httpPost, responseHandler)
            }

            if(verboseOutput) {
                println "Completed a REST call to POST ${fullPath}"
            }
        }

        return result
    }
}