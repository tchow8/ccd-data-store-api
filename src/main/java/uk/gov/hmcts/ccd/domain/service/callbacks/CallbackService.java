package uk.gov.hmcts.ccd.domain.service.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.ccd.AppInsights;
import uk.gov.hmcts.ccd.ApplicationParams;
import uk.gov.hmcts.ccd.data.SecurityUtils;
import uk.gov.hmcts.ccd.domain.model.callbacks.CallbackRequest;
import uk.gov.hmcts.ccd.domain.model.callbacks.CallbackResponse;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.definition.CaseEvent;
import uk.gov.hmcts.ccd.endpoint.exceptions.ApiException;
import uk.gov.hmcts.ccd.endpoint.exceptions.CallbackException;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.springframework.util.CollectionUtils.isEmpty;
import static uk.gov.hmcts.ccd.AppInsights.CALLBACK;

@Named
@Singleton
public class CallbackService {
    private static final Logger LOG = LoggerFactory.getLogger(CallbackService.class);

    private final SecurityUtils securityUtils;
    private final RestTemplate restTemplate;
    private final List<Integer> defaultRetries;
    private final AppInsights appInsights;

    public CallbackService(final SecurityUtils securityUtils,
                           RestTemplate restTemplate,
                           final ApplicationParams applicationParams,
                           final AppInsights appInsights) {
        this.securityUtils = securityUtils;
        this.restTemplate = restTemplate;
        this.defaultRetries = applicationParams.getCallbackRetries();
        this.appInsights = appInsights;
    }

    private Integer secondsToMilliseconds(final Integer seconds) {
        return seconds * 1000;
    }

    public Optional<CallbackResponse> send(final String url,
                                           final List<Integer> callbackRetries,
                                           final CaseEvent caseEvent,
                                           final CaseDetails caseDetails) {
        return send(url, callbackRetries, caseEvent, null, caseDetails);
    }

    public Optional<CallbackResponse> send(final String url,
                                           final List<Integer> callbackRetries,
                                           final CaseEvent caseEvent,
                                           final CaseDetails caseDetailsBefore,
                                           final CaseDetails caseDetails) {

        if (url == null || url.isEmpty()) {
            return Optional.empty();
        }

        final CallbackRequest callbackRequest = new CallbackRequest(caseDetails, caseDetailsBefore, caseEvent.getId());
        final List<Integer> retries = CollectionUtils.isEmpty(callbackRetries) ? defaultRetries : callbackRetries;

        for (Integer timeout : retries) {
            final Optional<ResponseEntity<CallbackResponse>> responseEntity = sendRequest(url,
                                                                                          CallbackResponse.class,
                                                                                          callbackRequest,
                                                                                          timeout);
            if (responseEntity.isPresent()) {
                return Optional.of(responseEntity.get().getBody());
            }
        }
        throw new CallbackException(getErrorMessage(url, caseEvent.getName(), caseDetails.getReference()));
    }

    public <T> ResponseEntity<T> send(final String url,
                                      final List<Integer> callbackRetries,
                                      final CaseEvent caseEvent,
                                      final CaseDetails caseDetailsBefore,
                                      final CaseDetails caseDetails,
                                      final Class<T> clazz) {

        final CallbackRequest callbackRequest = new CallbackRequest(caseDetails, caseDetailsBefore, caseEvent.getId());
        final List<Integer> retries = isEmpty(callbackRetries) ? defaultRetries : callbackRetries;

        for (Integer timeout : retries) {
            final Optional<ResponseEntity<T>> requestEntity = sendRequest(url, clazz, callbackRequest, timeout);
            if (requestEntity.isPresent()) {
                return requestEntity.get();
            }
        }
        // Sent so many requests and still got nothing, throw exception here
        throw new CallbackException(getErrorMessage(url, caseEvent.getName(), caseDetails.getReference()));
    }

    private <T> Optional<ResponseEntity<T>> sendRequest(final String url,
                                                        final Class<T> clazz,
                                                        final CallbackRequest callbackRequest,
                                                        final Integer timeout) {
        try {
            LOG.debug("Trying {} with timeout interval {}", url, timeout);

            final HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add("Content-Type", "application/json");

            final HttpHeaders securityHeaders = securityUtils.authorizationHeaders();
            if (null != securityHeaders) {
                securityHeaders.forEach((key, values) -> httpHeaders.put(key, values));
            }

            final HttpEntity requestEntity = new HttpEntity(callbackRequest, httpHeaders);
            final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();

            requestFactory.setConnectionRequestTimeout(secondsToMilliseconds(timeout));
            requestFactory.setReadTimeout(secondsToMilliseconds(timeout));
            requestFactory.setConnectTimeout(secondsToMilliseconds(timeout));
            restTemplate.setRequestFactory(requestFactory);
            final Instant start = Instant.now();
            Optional<ResponseEntity<T>> response = Optional.ofNullable(restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity, clazz));
            final Duration duration = Duration.between(start, Instant.now());
            LOG.debug("CallbackService sendRequest called for {}, finished in {}", url, duration.toMillis());
            appInsights.trackDependency(url, CALLBACK, duration.toMillis(), response.isPresent());
            return response;
        } catch (RestClientException e) {
            LOG.info("Unable to connect to callback service {} because of {} {}",
                     url,
                     e.getClass().getSimpleName(),
                     e.getMessage());
            LOG.debug("", e);  // debug stack trace
            return Optional.empty();
        }
    }

    public void validateCallbackErrorsAndWarnings(final CallbackResponse callbackResponse,
                                                  final Boolean ignoreWarning) {
        if (!isEmpty(callbackResponse.getErrors())
            || (!isEmpty(callbackResponse.getWarnings()) && (ignoreWarning == null || !ignoreWarning))) {
            throw new ApiException("Unable to proceed because there are one or more callback Errors or Warnings")
                .withErrors(callbackResponse.getErrors())
                .withWarnings(callbackResponse.getWarnings());
        }
    }

    private String getErrorMessage (String url, String eventName, Long caseReference) {
        StringBuilder msg = new StringBuilder("An error occurred while calling \"");
        msg.append(url)
            .append("\" for the callback on event \"")
            .append(eventName);
        if (caseReference != null) {
            msg.append("\" on case \"")
                .append(caseReference);
        }
        msg.append("\" at ");
        msg.append(LocalDateTime.now(ZoneOffset.UTC));
        msg.append(" - please contact the support team with this error message\"");
        return msg.toString();
    }
}
