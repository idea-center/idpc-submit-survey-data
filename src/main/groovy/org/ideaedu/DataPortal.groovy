package org.ideaedu

import idea.data.rest.RESTQuestionGroup

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

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

/**
 * The DataPortal class provides a layer of abstraction on top of real HTTP calls on the
 * IDEA Data Portal. This can be used to retrieve question groups and demographic groups as
 * well as submitting survey data.
 *
 * @author Todd Wallentine twallentine AT anthology com
 */
public class DataPortal {

    private protocol
    private hostname
    private port
    private basePath
    private verboseOutput
    private authHeaders
    private random

    public DataPortal(protocol, hostname, port, basePath, appName, appKey, verboseOutput) {
        // TODO: Check input values - throw exceptions if they fail

        this.protocol = protocol
        this.hostname = hostname
        this.port = port
        this.basePath = basePath
        this.verboseOutput = verboseOutput

        random = new Random() // TODO Should we seed it? -todd 11Jun2013

        authHeaders = [ 'X-IDEA-APPNAME': appName, 'X-IDEA-KEY': appKey ]
    }

    /**
     * Get the List of RESTQuestionGroup instances that are associated with the given formID. This will query the
     * REST API.
     *
     * @param int formID The form to get the question groups for.
     * @return The List of RESTQuestionGroup instances that are associated with the given form(formID).
     */
    public getQuestionGroups(formID) {
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
            println "Retrieved ${questionGroups.size()} question groups for form ${formID}."
        }

        return questionGroups
    }

    /**
     *  Get a random selection of demographic groups from the IDEA Data Portal.
     *
     * @param count The number of demographic groups to select; should be 0, 2, 3, or 4 to be valid when submitting to the Data Portal.
     * @return A collection of demographic groups; this might be empty but never null.
     */
    public getDemographicGroups(count) {
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
    public submitSurveyData(restSurvey) {

        def json = restSurvey.toJSON()
        if(verboseOutput) {
            println "JSON: ${json}"
        }

        try {
            def startTime = new Date().time

            def response = postData("services/survey", json)

            def endTime = new Date().time
            def runTime = endTime - startTime

            println "${response} -> ${runTime}ms"
        } catch (ex) {
            println 'Caught an exception while submitting the survey data:'
            println "${ex.message}"
            println "Cause: ${ex.cause}"
        }
    }

    /**
     * Perform an HTTP GET using the given path and return the response.
     *
     * @param path The relative path for the request; this is combined with the configured hostname, port, etc..
     * @return The result from the HTTP GET request in the form of a String.
     */
    private getData(path) {

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
    private postData(path, body) {
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