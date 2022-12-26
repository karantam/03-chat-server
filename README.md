Code for a simple chat server.

The server API is based on HTTPS and JSON.

Posting messages works by sending HTTP POST request to URL https://server.url/chat in the form 
{
		“user” : “nickname”,
		“message” : “contents of the message”,
		“sent” : “2020-12-21T07:57:47.123Z”
} 
and fetching messages from the server works by sending HTTP GET request to /chat.

Registering a user works by sending a POST request to /registration in the form 
{
		“username” : “username”,
		“password” : “password”,
		“email” : “user.email@for-contacting.com”
}

Requests other than registration require authentication, which is done over HTTPS using the Authorization header (username:password).

User administration, weather information, channels and modifying of messages have been implemented

User administration works by sending requests to /administration in the form
{
    “user” : "username",
    “action” : “edit”, //or "remove"
    “userdetails” :
    {
                    “username” : “username”,
                    “password” : “password”,
                    “role” : “user”, //or "admin"
                    “email” : user.email@for-contacting.com”
    }
}
Where user is the user being edited/removed. In the case of removing an user userdetails aren't needed.
The first user to register is automatically an admin and all users created after the first are normal users

Weather information works by adding location entry to a normal chat message
{
    “user” : “nickname”,
    “message” : “contents of the message”,
    “location” : “Oulu”,
    “sent” : “2020-12-21T07:57:47.123Z”
}

You can create channels by sending a POST requests to /channel in the form
{
	"channel" : "general"
}
or view created channels by sending a GET requests to /channel
You then use the channel by adding channel entry to a normal chat message.
GET request for messages on a channel are done by adding a header to a GET request
For example in curl this is done by -H "Channel: general"

Modifying of messages is done by sending requests to /chat in the form
{
    “user” : “nickname”,
    “action” : “editmessage” //or "deletemessage”"
    “messageid” : “8” 
    “message” : “new contents of the message”,
    “sent” : “2020-12-21T07:57:47.123Z”
}
Users can only edit and delete their own messages except for admins who can delete but not edit other users messages.
In the case of deleting a message the message field isn't needed. Deleting a message also deletes that message location and weather data.
All messages delivered by /chat GET requests have their messageid numbers shown at the beginning of the message.

Weather information, channels and modifying of messages can and sometimes must be used together.
For example to modify a message on a channel the request to modify it must contain the correct channel entry.
