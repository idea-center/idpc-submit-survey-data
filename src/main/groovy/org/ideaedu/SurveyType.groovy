package org.ideaedu

/**
 * @author Todd Wallentine twallentine AT anthology com
 */
public enum SurveyType {
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

    private SurveyType(name, aliases, infoFormID, raterFormID, isSRI) {
        this.name = name
        this.aliases = aliases
        this.infoFormID = infoFormID
        this.raterFormID = raterFormID
        this.isSRI = isSRI
    }

    static SurveyType get(name) {
        def surveyType

        values().each { type ->
            if((type.name.equalsIgnoreCase(name)) || (type.aliases.contains(name))) {
                surveyType = type
            }
        }

        return surveyType
    }
}