@ignore
Feature: Create loan stapes
  Background:
    * callonce read('classpath:features/base.feature')
    * url baseUrl
    * def productsData = read('classpath:templates/savings.json')

  @ignore
  @reviewLoanApplicationStage
  Scenario: Review Application Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/reviewApplication',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.reviewApplication
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }



  @ignore
  @dueDiligenceStage
  Scenario: Due Diligence Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/dueDiligence',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.dueDiligence
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @collateralReviewStage
  Scenario: Collateral Review Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/collateralReview',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.collateralReview
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @createLoanApprovalMatrixStep
  Scenario: Create Loan Approval Matrix Step
    Given configure ssl = true
    * def matrix = read('classpath:templates/loanApprovalMatrix.json')
    Given path 'loans/decision/approvalMatrix/createApprovalMatrix'
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request matrix.loanApprovalMatrix
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }
    Then def matrixId = response.resourceId


  @ignore
  @deleteLoanApprovalMatrixStep
  Scenario: Delete Loan Approval Matrix Step
    Given configure ssl = true
    Given path 'loans/decision/approvalMatrix/',matrixId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    When method DELETE
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @createLoanApprovalMatrixAndShouldFailDueToDuplicateCurrencyStep
  Scenario: Create Loan Approval Matrix and should fail due to duplicate Currency Step
    Given configure ssl = true
    * def matrix = read('classpath:templates/loanApprovalMatrix.json')
    Given path 'loans/decision/approvalMatrix/createApprovalMatrix'
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request matrix.loanApprovalMatrix
    When method POST
    Then status 403
    Then match $ contains { developerMessage: '#notnull' }



  @ignore
  @getAllLoanApprovalMatrixStep
  Scenario: Get All Loan Approval Matrix Step
    Given configure ssl = true
    Given path 'loans/decision/getAllApprovalMatrix'
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    When method GET
    Then status 200


  @ignore
  @getLoanApprovalMatrixStep
  Scenario: Get Loan Approval Matrix Step
    Given configure ssl = true
    Given path 'loans/decision/getApprovalMatrixDetails',matrixId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    When method GET
    Then status 200
    Then match $ contains { id: '#notnull' }

  @ignore
  @UpdateLoanApprovalMatrixStep
  Scenario: Update Loan Approval Matrix Step
    Given configure ssl = true
    * def matrix = read('classpath:templates/loanApprovalMatrix.json')
    Given path 'loans/decision/updateApprovalMatrix',matrixId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request matrix.updateLoanApprovalMatrix
    When method PUT
    Then status 200
    Then match $ contains { resourceId: '#notnull' }
    Then def matrixId = response.resourceId


  @ignore
  @icReviewDecisionLevelOneShouldFailStage
  Scenario: IC Review Decision Level One should Fail  Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/icReviewDecisionLevelOne',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.icReview
    When method POST
    Then status 403
    Then match $ contains { developerMessage: '#notnull' }


  @ignore
  @icReviewDecisionLevelOneStage
  Scenario: IC Review Decision Level One   Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/icReviewDecisionLevelOne',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.icReview
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @createLoanApprovalMatrixWithConfigurablePayLoadStep
  Scenario: Create Loan Approval Matrix With Configurable PayLoadStep
    Given configure ssl = true
    * def matrix = read('classpath:templates/loanApprovalMatrix.json')
    Given path 'loans/decision/approvalMatrix/createApprovalMatrix'
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request matrix.configurableLoanApprovalMatrix
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }
    Then def matrixId = response.resourceId

  @ignore
  @icReviewDecisionLevelTwoStage
  Scenario: IC Review Decision Level Two   Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/icReviewDecisionLevelTwo',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.icReview
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @icReviewDecisionLevelThreeStage
  Scenario: IC Review Decision Level Three   Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/icReviewDecisionLevelThree',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.icReview
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @icReviewDecisionLevelFourStage
  Scenario: IC Review Decision Level Four   Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/icReviewDecisionLevelFour',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.icReview
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @icReviewDecisionLevelFiveStage
  Scenario: IC Review Decision Level Five   Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/icReviewDecisionLevelFive',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.icReview
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @icReviewDecisionDynamicAcceptStage
  Scenario: Dynamic IC Review Decision Accept Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/icReviewDecision/level',levelNumber,loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.icReview
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @icReviewDecisionDynamicRejectStage
  Scenario: Dynamic IC Review Decision Reject Stage
    Given configure ssl = true
    * def rejectPayload =
    """
    {
      "rejectedOnDate": "#(rejectedOnDate)",
      "dateFormat": "#(format)",
      "locale": "en",
      "note": "#(faker.lorem().characters(70))"
    }
    """
    Given path 'loans/decision/icReviewDecision/level',levelNumber,'reject',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request rejectPayload
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }

  @ignore
  @prepareAndSignContractStage
  Scenario: Prepare And Sign Contract   Stage
    Given configure ssl = true
    * def loansData = read('classpath:templates/loansDecision.json')
    Given path 'loans/decision/prepareAndSignContract',loanId
    And header Accept = 'application/json'
    And header Authorization = authToken
    And header fineract-platform-tenantid = tenantId
    And request loansData.icReview
    When method POST
    Then status 200
    Then match $ contains { resourceId: '#notnull' }