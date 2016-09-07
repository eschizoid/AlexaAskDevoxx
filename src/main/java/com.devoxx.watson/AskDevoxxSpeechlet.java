/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.devoxx.watson;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.*;
import com.amazon.speech.ui.*;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.amazonaws.util.json.JSONTokener;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;


/**
 * This sample shows how to create a Lambda function for handling Alexa Skill requests that:
 * <ul>
 * <li><b>Web service</b>: communicate with an external web service to get tide data from NOAA
 * CO-OPS API (http://tidesandcurrents.noaa.gov/api/)</li>
 * <li><b>Multiple optional slots</b>: has 2 slots (city and date), where the user can provide 0, 1,
 * or 2 values, and assumes defaults for the unprovided values</li>
 * <li><b>DATE slot</b>: demonstrates date handling and formatted date responses appropriate for
 * speech</li>
 * <li><b>Custom slot type</b>: demonstrates using custom slot types to handle a finite set of known values</li>
 * <li><b>SSML</b>: Using SSML tags to control how Alexa renders the text-to-speech</li>
 * <li><b>Pre-recorded audio</b>: Uses the SSML 'audio' tag to include an ocean wave sound in the welcome response.</li>
 * <p>
 * - Dialog and Session state: Handles two models, both a one-shot ask and tell model, and a
 * multi-turn dialog model. If the user provides an incorrect slot in a one-shot model, it will
 * direct to the dialog model. See the examples section for sample interactions of these models.
 * </ul>
 * <p>
 * <h2>Examples</h2>
 * <p>
 * <b>One-shot model</b>
 * <p>
 * User: "Alexa, ask Tide Pooler when is the high tide in Seattle on Saturday" Alexa: "Saturday June
 * 20th in Seattle the first high tide will be around 7:18 am, and will peak at ...""
 * <p>
 * <b>Dialog model</b>
 * <p>
 * User: "Alexa, open Tide Pooler"
 * <p>
 * Alexa: "Welcome to Tide Pooler. Which city would you like tide information for?"
 * <p>
 * User: "Seattle"
 * <p>
 * Alexa: "For which date?"
 * <p>
 * User: "this Saturday"
 * <p>
 * Alexa: "Saturday June 20th in Seattle the first high tide will be around 7:18 am, and will peak
 * at ..."
 */
public class AskDevoxxSpeechlet implements Speechlet {
  private static final Logger log = LoggerFactory.getLogger(AskDevoxxSpeechlet.class);

  private static final String SLOT_COMMAND = "Command";
  private static final String INQUIRY_ENDPOINT = "https://askdevoxx.cfapps.io/inquiry";

  private static final String devoxxImageUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS2EJZCArvYTVTThseT3TdN25cmPRanxrM2RDAgOI1GT0GEQLMVLA";

  @Override
  public void onSessionStarted(final SessionStartedRequest request, final Session session)
          throws SpeechletException {
      log.info("onSessionStarted requestId={}, sessionId={}", request.getRequestId(),
              session.getSessionId());

      // any initialization logic goes here
  }

  @Override
  public SpeechletResponse onLaunch(final LaunchRequest request, final Session session)
          throws SpeechletException {
      log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
              session.getSessionId());

      return getWelcomeResponse();
  }

  @Override
  public SpeechletResponse onIntent(final IntentRequest request, final Session session)
          throws SpeechletException {
      log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
              session.getSessionId());

      Intent intent = request.getIntent();
      String intentName = intent.getName();

      Slot commandSlot = intent.getSlot(SLOT_COMMAND);
      if (commandSlot != null && commandSlot.getValue() != null) {
          log.info("I received a Command request: " + commandSlot.getValue());
      }
      else {
          log.info("I'm not sure if I received a request");
      }


      if ("OneShotCommandIntent".equals(intentName)) {
          return handleOneShotCommandRequest(intent, session);
      }

      /*
      else if ("AMAZON.HelpIntent".equals(intentName)) {
          return handleHelpRequest();
      }
      */

      else if ("AMAZON.StopIntent".equals(intentName)) {
          PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
          outputSpeech.setText("Goodbye");

          return SpeechletResponse.newTellResponse(outputSpeech);
      } else if ("AMAZON.CancelIntent".equals(intentName)) {
          PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
          outputSpeech.setText("Goodbye");

          return SpeechletResponse.newTellResponse(outputSpeech);
      } else {
          throw new SpeechletException("Invalid Intent");
      }
  }

  @Override
  public void onSessionEnded(final SessionEndedRequest request, final Session session)
          throws SpeechletException {
      log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
              session.getSessionId());
  }


  private SpeechletResponse getWelcomeResponse() {
      String whatIsYourQuestionPrompt = "What is your question?";
      String speechOutput = "<speak>"
          + "Welcome to Ask Devoxx. "
          + whatIsYourQuestionPrompt
          + "</speak>";
      String repromptText =
          "You can simply say Ask Devoxx and ask a question like, "
              + "what is Devoxx U.S. ";

      return newAskResponse(speechOutput, true, repromptText, false);
  }

  /**
   * This handles the one-shot interaction, where the user utters a phrase like: 'Alexa, open Tide
   * Pooler and get tide information for Seattle on Saturday'. If there is an error in a slot,
   * this will guide the user to the dialog approach.
   */
  private SpeechletResponse handleOneShotCommandRequest(final Intent intent, final Session session) {
      Slot commandSlot;
      String speechOutput;

      try {
          commandSlot = intent.getSlot(SLOT_COMMAND);
      } catch (Exception e) {
          // invalid city. move to the dialog
          speechOutput =
                  "I need a Command";

          // repromptText is the same as the speechOutput
          return newAskResponse(speechOutput, speechOutput);
      }


      // all slots filled, either from the user or by default values. Move to final request
      speechOutput =
          "Command is " + commandSlot.getValue();

    //return makeInquiryRequest("What is Devoxx US?");
    return makeInquiryRequest(commandSlot.getValue());
  }

  /**
   * Call a ConceptMap endpoint to retrieve related claims
   *
   * @throws IOException
   */
  //private SpeechletResponse makeClaimsRequest(String itemId, String propId) {
  private SpeechletResponse makeInquiryRequest(String commandValue) {
    System.out.println("commandValue: " + commandValue);
    String speechOutput = "";
    Image image = new Image();

    if (commandValue != null && commandValue.length() > 0) {
      String escapedInquiry = commandValue;

      try {
        escapedInquiry = URLEncoder.encode(commandValue, "UTF-8");
      }
      catch (UnsupportedEncodingException uee) {}

      String inquiryString =
          String.format("?text=%s", escapedInquiry);

      // Append a question mark to the end of the inquiry if not already present
      if (inquiryString.charAt(escapedInquiry.length() - 1) != '?') {
        inquiryString += "?";
      }

      InputStreamReader inputStream = null;
      BufferedReader bufferedReader = null;
      StringBuilder builder = new StringBuilder();
      try {
        String line;
        URL url = new URL(INQUIRY_ENDPOINT + inquiryString);

        System.out.println("url: " + url);

        inputStream = new InputStreamReader(url.openStream(), Charset.forName("US-ASCII"));
        bufferedReader = new BufferedReader(inputStream);
        while ((line = bufferedReader.readLine()) != null) {
          builder.append(line);
        }
      } catch (IOException e) {
        e.printStackTrace();
        // reset builder to a blank string
        builder.setLength(0);
      } finally {
        IOUtils.closeQuietly(inputStream);
        IOUtils.closeQuietly(bufferedReader);

        //log.info("builder: " + builder);
        System.out.println("builder: " + builder);
      }

      if (builder.length() == 0) {
        speechOutput =
            "Sorry, the Ask Devoxx inquiry service is experiencing a problem. "
                + "Please try again later.";
      } else {
        try {
          JSONObject inquiryResponseObject = new JSONObject(new JSONTokener(builder.toString()));

          if (inquiryResponseObject != null) {
            InquiryResponseInfo inquiryResponseInfo = createInquiryResponseInfo(inquiryResponseObject);

            log.info("inquiryResponseInfo: " + inquiryResponseInfo);

            String inquiryResponseText = inquiryResponseInfo.getResponseText();
            if (inquiryResponseText != null && inquiryResponseText.length() > 0) {
              speechOutput = new StringBuilder()
                  .append(inquiryResponseText)
                  .append(" \n")
                  .toString();
            }
            else {
              speechOutput = "Unable to respond to your inquiry\n";
            }

            // Get the picture
            image.setSmallImageUrl(devoxxImageUrl);
          }
          //} catch (JSONException | ParseException e) {
        } catch (JSONException e) {
          log.error("Exception occoured while parsing service response.", e);
        }
      }
    }
    else {
      speechOutput = "Invalid inquiry; " + commandValue;
    }

    // Create the Simple card content.
    StandardCard card = new StandardCard();
    card.setTitle(commandValue);
    card.setText(speechOutput);
    card.setImage(image);
    // Create the plain text output
    PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
    outputSpeech.setText(speechOutput);

    return SpeechletResponse.newTellResponse(outputSpeech, card);
  }

  /**
   * Create an object that contains claims info
   */
  private InquiryResponseInfo createInquiryResponseInfo(JSONObject inquiryResponseObject) throws JSONException { //, ParseException {
    InquiryResponseInfo inquiryResponseInfo = new InquiryResponseInfo();
    String responseText = inquiryResponseObject.getString("responseText");

    JSONArray resourcesJsonArray = (JSONArray)inquiryResponseObject.get("resources");

    // TODO: Put these in a loop when requirements are solid
    if (resourcesJsonArray.length() > 0 ) {
      JSONObject firstResourceJson = (JSONObject)resourcesJsonArray.get(0);
      String firstResourceBodyText = firstResourceJson.getString("body");
      responseText += "\n" + firstResourceBodyText;
    }
    if (resourcesJsonArray.length() > 1 ) {
      JSONObject secondResourceJson = (JSONObject)resourcesJsonArray.get(1);
      String secondResourceBodyText = secondResourceJson.getString("body");
      responseText += "\n" + secondResourceBodyText;
    }

    inquiryResponseInfo.setResponseText(responseText);
    return inquiryResponseInfo;
  }


  /**
   * Wrapper for creating the Ask response from the input strings with
   * plain text output and reprompt speeches.
   *
   * @param stringOutput
   *            the output to be spoken
   * @param repromptText
   *            the reprompt for if the user doesn't reply or is misunderstood.
   * @return SpeechletResponse the speechlet response
   */
  private SpeechletResponse newAskResponse(String stringOutput, String repromptText) {
      return newAskResponse(stringOutput, false, repromptText, false);
  }

  /**
   * Wrapper for creating the Ask response from the input strings.
   *
   * @param stringOutput
   *            the output to be spoken
   * @param isOutputSsml
   *            whether the output text is of type SSML
   * @param repromptText
   *            the reprompt for if the user doesn't reply or is misunderstood.
   * @param isRepromptSsml
   *            whether the reprompt text is of type SSML
   * @return SpeechletResponse the speechlet response
   */
  private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml,
          String repromptText, boolean isRepromptSsml) {
      OutputSpeech outputSpeech, repromptOutputSpeech;
      if (isOutputSsml) {
          outputSpeech = new SsmlOutputSpeech();
          ((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
      } else {
          outputSpeech = new PlainTextOutputSpeech();
          ((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
      }

      if (isRepromptSsml) {
          repromptOutputSpeech = new SsmlOutputSpeech();
          ((SsmlOutputSpeech) repromptOutputSpeech).setSsml(stringOutput);
      } else {
          repromptOutputSpeech = new PlainTextOutputSpeech();
          ((PlainTextOutputSpeech) repromptOutputSpeech).setText(repromptText);
      }

      Reprompt reprompt = new Reprompt();
      reprompt.setOutputSpeech(repromptOutputSpeech);
      return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
  }
}
