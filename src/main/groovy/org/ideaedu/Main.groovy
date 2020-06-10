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

import org.joda.time.LocalDate
import java.util.*

import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType

import groovy.json.JsonBuilder

import groovy.time.*

import java.util.Random

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
	private static final String DEFAULT_BASE_PATH = 'v1/' //use v1/ for RDS
    private static final def DEFAULT_AUTH_HEADERS = [ 'X-IDEA-APPNAME': '', 'X-IDEA-KEY': '' ]
    private static final String DEFAULT_TYPE = SURVEY_TYPE.DIAGNOSTIC
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

	private static RESTClient restClient

	private static Random random = new Random() // TODO Should we seed it? -todd 11Jun2013

	static void main(String[] args) {

		/*
		 * TODO Other command line options that might be useful:
		 * 1) data file - contents define the answers to info form and rater form questions
		 * 2) year, term, start/end date, gap analysis flag
		 */
		def cli = new CliBuilder( usage: 'Main -v -s -h host -p port -b basePath -sid srcID -sgid srcGroupID -iid instID -a "TestClient" -k "ABCDEFG123456" -t "diag" -d 5120 -es 1 -eo 1 -ras 10 -ran 9 -sn 10' )
		cli.with {
			v longOpt: 'verbose', 'verbose output'
            s longOpt: 'ssl', 'use SSL (default: false)'
			h longOpt: 'host', 'host name (default: localhost)', args: 1
			p longOpt: 'port', 'port number (default: 8091)', args: 1
			b longOpt: 'basePath', 'base REST path (default: IDEA-REST-SERVER/v1/', args: 1
			sid longOpt: 'srcID', 'source ID', args: 1
			sgid longOpt: 'srcGroupID', 'source Group ID', args: 1
			iid longOpt: 'institutionID', 'institution ID', args: 1
			a longOpt: 'app', 'client application name', args: 1
			k longOpt: 'key', 'client application key', args: 1
			t longOpt: 'type', 'survey type', args: 1
            c longOpt: 'cip', 'cip code', args: 1
            ras longOpt: 'asked', 'number of respondents asked to respond', args: 1
            ran longOpt: 'answered', 'number of respondents that responded', args: 1
			sn longOpt: 'surveys', 'number of surveys', args: 1
            de longOpt: 'demographics', 'demographic groups', args: 1
            es longOpt: 'extraScaled', 'extra scaled questions', args: 1
            eo longOpt: 'extraOpen', 'extra open questions', args: 1
			'?' longOpt: 'help', 'help'
		}
		def options = cli.parse(args)
		if(options.'?') {
			cli.usage()
			return
		}
		if(options.v) {
			verboseOutput = true
		}
        if(options.s) {
            protocol = 'https'
        }
		if(options.h) {
			hostname = options.h
		}
		if(options.p) {
			port = options.p.toInteger()
		}
		if(options.b) {
			basePath = options.b
		}
		if(options.sid) {
			srcID = options.sid
		}
		if(options.sgid) {
			srcGroupID = options.sgid
		}
		if(options.iid) {
			institutionID = options.iid.toInteger()
		}
		if(options.a) {
			authHeaders['X-IDEA-APPNAME'] = options.a
		}
		if(options.k) {
			authHeaders['X-IDEA-KEY'] = options.k
		}
		if(options.t) {
            type = SURVEY_TYPE.get(options.t)
		}
        if(options.c) {
            cipCode = options.c
        }
        if(options.ras) {
            numAsked = options.ras.toInteger()
            numAnswered = numAsked // default 100% response rate
        }
        if(options.ran) {
            numAnswered = options.ran.toInteger()
        }
		if (options.sn) {
			numSurveys = options.sn.toInteger()
		}
        if(options.de) {
            demographicGroupCount = options.de.toInteger()
        }
        if(options.es) {
            extraScaledQuestionCount = options.es.toInteger()
        }
        if(options.eo) {
            extraOpenQuestionCount = options.eo.toInteger()
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

		def infoFormID = type.infoFormID
		def raterFormID = type.raterFormID

        def programCode = cipCode

        def demographicGroups = getDemographicGroups(demographicGroupCount)
        def demographicGroupIDs
        if(demographicGroups) {
            demographicGroupIDs = demographicGroups.collect { it.id }
        }

		def restInfoForm = buildRESTInfoForm(infoFormStartDate, infoFormEndDate, type, null, type.isSRI? programCode:null)
		def course
		if(type.isSRI) {
			course = buildRESTCourse(courseStartDate? new LocalDate(courseStartDate):null, courseEndDate? new LocalDate(courseEndDate):null)
		}
        def restRaterForm = buildRESTRaterForm(raterFormStartDate, raterFormEndDate, numAsked, numAnswered,
          raterFormID, extraScaledQuestionCount, extraOpenQuestionCount, demographicGroups)
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

		def client = getRESTClient()
		if(verboseOutput) {
            println "Retrieving questions for form ${formID}..."
        }
		def r = client.get(
                path: "${basePath}/forms/${formID}/questions",
                headers: authHeaders)

		r.data.data.each { qg ->
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
            def client = getRESTClient()
            if(verboseOutput) {
                println "Retrieving a program code for discipline code ${disciplineCode}..."
            }

            def r = client.get(
                path: "${basePath}/cip",
                query: [discipline_code: disciplineCode],
                headers: authHeaders)

            programCode = r?.data?.data?.first().cip_code

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
            def r = client.get(
                path: "${basePath}/demographic_groups",
                headers: authHeaders)
            def subGroups = []
            r?.data?.data.each { it ->
                def jsonBuilder = new JsonBuilder(it)
                def json = jsonBuilder.toString()
                subGroups << RESTSubGroup.fromJSON(json)
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
		def client = getRESTClient()
		try {
            def startTime = new Date().time

			def response = client.post(
				path: "${basePath}/services/survey",
				body: json,
				requestContentType: ContentType.JSON,
				headers: authHeaders)

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
	 * Get an instance of the RESTClient that can be used to access the REST API.
	 *
	 * @return RESTClient An instance that can be used to access the REST API.
	 */
	private static getRESTClient() {
		if(restClient == null) {
			if(verboseOutput) {
                println "REST requests will be sent to ${hostname} on port ${port} (protocol: ${protocol})"
            }
			restClient = new RESTClient("${protocol}://${hostname}:${port}/")
            restClient.ignoreSSLIssues()
            restClient.handler.failure = { response ->
                if(verboseOutput) {
                    println "The REST call failed: ${response.status}"
                }
                return response
            }
		}

		return restClient
	}
}