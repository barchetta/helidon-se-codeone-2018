"use strict";
/* global EventSource, getCurrentUser, gapi, Mustache */

// https://developers.google.com/identity/sign-in/web/reference
// https://developers.google.com/identity/sign-in/web/devconsole-project

function isObject(val) {
    if (val === null) {
        return false;
    }
    return ((typeof val === 'function') || (typeof val === 'object'));
}

function isFunction(obj) {
    return !!(obj && obj.constructor && obj.call && obj.apply);
}

function __put(url, data, on_complete) {
    var access_token = getAccessToken();

    console.log("__put initiated", url, data);

    try {
        $.ajax({
            type: "PUT",
            beforeSend: function (request) {
                if (access_token !== undefined) {
                    request.setRequestHeader("Authorization", "Bearer " + access_token);
                }
            },
            url: url,
            dataType: "json",
            contentType: "application/json;charset=utf-8",
            data: JSON.stringify(data)
        }).done(function (resData) {
            console.log("__put success", url, resData);
            if (isFunction(on_complete)) {
                on_complete(resData);
            }
        }).fail(function (data, textStatus, xhr) {
            console.log("Failed in __put", url, data, textStatus, xhr);
        });
    } catch (err) {
        console.log("Error in __put", url, err);
    }
}

function __get(url, on_complete) {
    var access_token = getAccessToken();

    console.log("__get initiated", url);

    try {
        $.ajax({
            type: "GET",
            beforeSend: function (request) {
                if (access_token !== undefined) {
                    request.setRequestHeader("Authorization", "Bearer " + access_token);
                }
            },
            url: url
        }).done(function (data) {
            console.log("__get success", url, data);
            if (isFunction(on_complete)) {
                on_complete(data);
            }
        }).fail(function (data, textStatus, xhr) {
            console.log("Error in __get",url, data, textStatus, xhr);
        });
    } catch (err) {
        console.log("Error in __get", url, err);
    }
}

function renderUpdateGreeting(greeting) {
    var template = $('#update_greeting_tpl').html();
    Mustache.parse(template);
    var rendered = Mustache.render(template, {
        "greeting" : greeting
    });
    var elt = $("#update-greeting-section");
    $(elt).html(rendered);
}

function loadPage(signedIn) {
    var greetingForm = $("#greeting-form");
    greetingForm.find("button").click(doGreeting);
    if(!signedIn){
        $("#page-wrapper").fadeIn(100);
        return;
    }

    __get("/greet/greeting", function (data) {
        if (data.length === 0 || data.greeting === undefined) {
            clearPageError("Error: unable to get current greet value.");
            return;
        }

        renderUpdateGreeting(data.greeting);

        var updateGreetingForm = $("#update-greeting-form");
        updateGreetingForm.find("button").click(doUpdateGreeting);

        $("#page-wrapper").fadeIn(100);
    });
}

function displayResult(msg){
    var result_modal = $("#result-modal");
    var resul_p = $(result_modal).find("p");
    $(resul_p).text(msg);
    $(result_modal).modal({
        backdrop: true,
        keyboard: false
    });
    $(result_modal).modal("show");
}

function displayGreetResult(data){
    displayResult(data.message);
}

function doGreeting(){
    var name = $("#greeting-form").find("input").val();
    if(name !== undefined){
        __get("/greet/" + name, displayGreetResult);
    } else {
        __get("/greet", displayGreetResult);
    }
}

function displayUpdateGreetingResult(data){
    displayResult("Greeting value updated to: " + data.greeting);
}

function doUpdateGreeting(){
    var greeting = $("#update-greeting-form").find("input").val();
    if(greeting !== undefined){
        __put("/greet/greeting/" + greeting, null, displayUpdateGreetingResult);
    } else {
        displayError("greeting is empty");
    }
}

function loadPage0() {

    var signedIn = isSignedIn();
    if (signedIn) {

        var signinbtn = $("#signin-btn");
        $(signinbtn).hide();

        // signed-in
        var userinfo = $("#userinfo");
        $(userinfo).find("#email-text").text(getCurrentUser().getBasicProfile().getEmail());

        // add click handler for signout btn
        $("#signout-btn").click(function () {
            var auth2 = gapi.auth2.getAuthInstance();
            auth2.signOut().then(clearPageSignIn());
        });

        if (!$(userinfo).is(':visible')) {
            $(userinfo).fadeIn();
        }

    } else {
        // not signed-in
        var userinfo = $("#userinfo");
        $(userinfo).hide();
        var signinbtn = $("#signin-btn");
        if (!$(signinbtn).is(':visible')) {
            $(signinbtn).fadeIn();
        }
    }
    loadPage(signedIn);
}

function clearPage(on_complete) {
    $("#update-greeting-section").html("");
    $("#page-wrapper").fadeOut(100, function(){
        if (isFunction(on_complete)) {
            on_complete();
        }
    });
}

function clearPageSignIn() {
    // invoked if not signed-in
    // or upon sign-out
    clearPage(function () {
        var userinfo = $("#userinfo");
        $(userinfo).find("#email-text").text("");
        loadPage0();
    });
}

function displayError(error_msg){
    var error_modal = $("#error-modal");
    var error_p = $(error_modal).find("p");
    $(error_p).text(error_msg);
    $(error_modal).modal({
        backdrop: true,
        keyboard: false
    });
    $(error_modal).modal("show");
}

function clearPageError(data) {
    // invoked if auth error
    console.log("clearPageError", data);

    clearPage(function () {
        // set the error message
        var error_msg = "";
        if (!isObject(data)) {
            error_msg = data;
        } else {
            // object has an 'error' field
            if (data.error) {
                error_msg = data.error;
            } else if (data.message) {
                // object has a 'message' field
                error_msg = data.message;
            } else {
                // put all values
                for (var elem in data) {
                    error_msg += data[elem];
                    error_msg += "<br>";
                }
            }
        }
        displayError(error_msg);
    });
}

function getCurrentUser() {
    return gapi.auth2.getAuthInstance().currentUser.get();
}

function isSignedIn() {
    return getAccessToken() !== undefined;
}

function getAccessToken() {
    try {
        var access_token = getCurrentUser().getAuthResponse().id_token;
        if (access_token === undefined) {
            return undefined;
        }
        return access_token;
    } catch (err) {
        clearPageError(err);
        return undefined;
    }
}
