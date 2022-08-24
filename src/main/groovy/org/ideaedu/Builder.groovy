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

/**
 * The Builder class provides a simpler way to build out the data that will be used to submit sample data
 * to the IDEA Data Portal.
 *
 * @author Todd Wallentine twallentine AT anthology com
 */
public class Builder {

    private verboseOutput
    private random

    public Builder(verboseOutput) {
        this.verboseOutput = verboseOutput
        random = new Random() // TODO Should we seed it? -todd 11Jun2013
    }

    /**
     * Build an instance of RESTCourse.
     *
     * @return A new RESTCourse that can be used in a RESTSurvey.
     */
    public buildRESTCourse(startDate, endDate) {
        def section = buildRESTSection(startDate, endDate)
        def restCourse = new RESTCourse(title: 'Intro to IDEA', number: 'IDEA 101', localCode: '0 234 67', days: 'MTWUF', time: '08:00',
                                        srcId: 'courseSrcId', subject: 'course subject', type: 'undergraduate', deliveryMode: 'Face to Face',
                                        termType: 'semester', startDate: startDate, endDate: endDate, section: section)
        return restCourse
    }

    /**
     * Build an instance of RESTSection.
     *
     * @return A new RESTSection that can be used in a RESTCourse.
     */
    private buildRESTSection(startDate, endDate) {
        def restSection = new RESTSection(title: 'Intro to IDEA - Section I', number: 'IDEA 101 - Sec I', srcId: 'sectionSrcId', subject: 'section subject',
                                          deliveryMode: 'Face to Face', startDate: startDate, endDate: endDate, localCode: '9 87 6 4', days: 'MWF', time: '09:00')
        return restSection
    }

    /**
     * Build an instance of RESTForm that is a rater form that has the given number of respondents (numberAsked),
     * starts on the date given (startDate) and ends on the date given (endDate).
     *
     * @param startDate The date that this rater form will open.
     * @param endDate The date that this rater form will close.
     * @param numberAsked The number of respondents that are asked to respond to this survey.
     * @param numberAnswered The number of respondents that answered questions in this survey.
     * @param raterFormID The ID of the form to use.
     * @param questionGroups The question groups to include in the rater form.
     * @param extraScaled The number of extra scaled questions to create (default is 0).
     * @param extraOpen the number of extra open questiosn to create (default is 0).
     * @param demographicGroups The demographic groups to use when building this form.
     * @return RESTForm A new RESTForm instance that is populated with test data.
     */
    public buildRESTRaterForm(startDate, endDate, numberAsked, numberAnswered, raterFormID, questionGroups,
        extraScaled=DEFAULT_EXTRA_SCALED_QUESTION_COUNT, extraOpen=DEFAULT_EXTRA_OPEN_QUESTION_COUNT,
        demographicGroups) {

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
    private selectDemographicGroup(demographicGroups) {
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
    private buildExtraQuestionGroups(extraScaled=DEFAULT_EXTRA_SCALED_QUESTION_COUNT, extraOpen=DEFAULT_EXTRA_OPEN_QUESTION_COUNT) {
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
     * @param SurveyType The type of the survey this information form will be associated with (Chair, Diagnostic, etc.).
     * @param questionGroups The question groups to use for this information form.
     * @param String disciplineCode The discipline code to use for the given course (if SRI).
     * @param String programCode The program code to use for the given course (if SRI).
     * @return RESTForm A new RESTForm instance that is populated with test data.
     */
    public buildRESTInfoForm(startDate, endDate, type, questionGroups, disciplineCode, programCode=DEFAULT_PROGRAM_CODE) {

        def title
        if(type == SurveyType.DIAGNOSTIC) {
            title = 'Assistant Professor'
        } else if(type == SurveyType.SHORT) {
            title = 'Associate Professor'
        } else if(type == SurveyType.LEARNING_ESSENTIALS) {
            title = 'Adjunct Professor'
        } else if(type == SurveyType.DIAGNOSTIC_2016) {
            title = 'Professor'
        } else if(type == SurveyType.TEACHING_ESSENTIALS) {
            title = 'Instructor'
        } else if(type == SurveyType.CHAIR) {
            title = 'Chair'
        } else if(type == SurveyType.ADMIN) {
            title = 'Vice Provost'
        } else if(type == SurveyType.ADVISOR
                || type == SurveyType.ADVISING_STAFF
                || type == SurveyType.ADVISING_STUDENT) {
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
    private buildRESTResponses(questionGroups, customQuestionGroups=[]) {
        def responses = [] as Set

        questionGroups?.each { questionGroup ->
            questionGroup.questions?.each() { question ->
                int numAnswers = 1

                //println "Question: $question.id, $question.type, $question.text"
                if (question.type == 'multipleChoiceMultipleAnswer') {
                    def responseOptions = getResponseOptions(question, questionGroup)
                    if (responseOptions) {
                        numAnswers = random.nextInt(responseOptions.size)
                        println "multipleChoiceMultipleAnswer question detected, choosing ${numAnswers} responses"
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
    private getRandomAnswer(question, questionGroup) {
        def answer

        // Priority is given to question response options. If not defined, use the question group response options.
        if(getResponseOptions(question, questionGroup)) {
            answer = getValue(getResponseOptions(question, questionGroup))
        } else {
            answer = "Test Answer ${random.nextInt()}"
        }

        return answer
    }

    private getResponseOptions(question, questionGroup) {
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
    private getValue(responseOptions) {
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
}