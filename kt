Transcript
October 28, 2025, 5:26PM

Inayat, Sajid started transcription

Bucko, Ahmet   0:05
70% or more the AI share about it.

Peti, Helios   0:09
OK, OK, I understand.

Inayat, Sajid   0:09
So question for you, this e-mail that you have shown on this web page, right? It came into Outlook, right? From Outlook to this page, how did it get transmitted?

Bucko, Ahmet   0:18
Yes.
Yeah, basically they have a service which waits for the for an e-mail to come and when that e-mail comes they read the attachment and that attachment is being sent on the web service and that web service pulls data.

Peti, Helios   0:24
I think.

Inayat, Sajid   0:32
OK, I see.
I see.
Hmm.

Bucko, Ahmet   0:42
Like does the data extraction and sends it to an AI service. So basically this AI service asks some questions and returns the predefined Jason with file. So and then this Jason gets saved to a database and then you get inside like manually.

Inayat, Sajid   0:53
Oh.

Bucko, Ahmet   1:01
And you fill out the data, you know like now here you can see like for this red one, the user is not the AI was not sure you know they have more than 50% of success rate.
Less than 50%. This one is above 70%. Like we are sure that more than 70% AI was sure that they are it is 5858 years old and then the user puts it, saves it the data.
And then the user puts it, saves it the data.
Then they validate. They have some predefined validations put in place and then the manual review where everything gets passed when passed through submit. Basically this submit generates an XML response, an XML file.

Peti, Helios   1:45
Oh.

Bucko, Ahmet   1:50
Which sends out to a different system, which now they open and review it again. But here our work is done, Mike. Yep, we deal with you.

Peti, Helios   2:00
I have I I have a question. OK, so First thing first, I think we use Ms. Graph right to get the e-mail connection.

Bucko, Ahmet   2:05
Yes.
I'm not sure that. Yeah, basically the developer guys doesn't know it's like really there is a web app and they know AI. There is an AI functionality. Yep, and they got a JSON response. What happens to that AI?

Peti, Helios   2:15
It's OK, yeah, yeah.
OK, it's OK. So basically.
OK.
I just.

Bucko, Ahmet   2:29
Service. You know they are not sure. I asked them. Do you do any embedding? How do you how do you pull out data? Yep, they were not aware.

Peti, Helios   2:34
OK.
Also say one one thing we should keep in mind here that we didn't know. So if we do the extraction and we validated via Shima this extraction then we'll go to the to the other which will create the XML.
Version of our schema. So will we do also the the I would say the the parsing from the Jason to XML based on the XML fields that they require or will it be done by the other team?
So are we only doing the extraction and saving to Jason and then they can handle the other XML schema to do the parsing OK?

Bucko, Ahmet   3:20
Basically they need the manual intervention at the moment. This is, yeah, basically they have a lot of code in here, you know. So basically, Yep, that part, whenever you feel that Jason, Yep, you will do it. The AI part will do it. But then the XML part and stuff, it will be done by our like our system here.

Peti, Helios   3:32
Yeah, yeah.
OK, OK. So basically we are on me and said we're only going to take part of the extraction thing using Azure document intelligence, using both another lamp to improve the enhancement parts validating via Shima and just sending you guys back to Jason in the front end and then.
You guys move on from there, right?

Bucko, Ahmet   3:58
Yes, yes, basically they have a way like you update stuff, save them.

Peti, Helios   3:59
OK.

Bucko, Ahmet   4:05
And yeah, but this is it. Submit and.

Peti, Helios   4:09
Yeah, I I understand. I can see.
OK.

Inayat, Sajid   4:24
And from a performance perspective, is taking, um, how many seconds?

Peti, Helios   4:34
I think it depends also on the PDF file. How big is it is I will assume right?

Inayat, Sajid   4:39
Mm.
Oh.

Peti, Helios   4:45
Because if the PDF is, let's say 5 megabytes, I think it will take more than like the PDF was just like 200 kilobytes, right?

Bucko, Ahmet   4:50
Yeah. Can you hear me guys now? Yep.

Inayat, Sajid   4:51
Yeah. And we probably have. Yeah. No, no, we can hear you.

Peti, Helios   4:54
Yeah.

Bucko, Ahmet   4:54
Yep, my Internet went down. Sorry.

Inayat, Sajid   4:58
Yeah. So I think we were discussing that the performance is dependent on how big the PDF or image could be and depending on how many pages you have to process, it will take longer than expected, right? But there might be a way to actually identify typically, let's say.

Bucko, Ahmet   5:13
Yes.

Inayat, Sajid   5:18
3% of the time they get 5 pages in a PDF. For example, there has to be some sort of pattern, right? Typical PDF contains how many pages? I'm sure they probably have some some stats there.

Bucko, Ahmet   5:29
We we touched it also, yes.

Inayat, Sajid   5:35
Hmm.

Bucko, Ahmet   5:36
Yes. And basically they have from one page to 55 pages, Yep.

Inayat, Sajid   5:39
55 pages, but there's no percentage associated how the volume looks like day in a life scenario.

Bucko, Ahmet   5:41
Yes.
No. Yeah, basically like from a single source, you know, let's say from Pfizer, you know they will get known, known.
PDF file like like we have, the format would be the same, you know, and from another pharma company, yeah, basically they're going to have the same, the same page layout of the PDF.

Peti, Helios   6:03
Oh.

Bucko, Ahmet   6:14
And yeah, but they want to build something for those, like for those known company that they're receiving events from.

Inayat, Sajid   6:14
OK.
Oh.

Bucko, Ahmet   6:22
Uh, basically they want to have something like, you know, like really good parser there.

Inayat, Sajid   6:28
Yeah, we'll have to, yeah, we were thinking to actually use the document intelligence. So we'll we'll use inbuilt capability, but we'll probably use the custom model in DI on Azure side. We are going to move away from tech select actually.

Bucko, Ahmet   6:29
And maybe.

Inayat, Sajid   6:46
That's the thought process. We'll we'll work with Yashan team to, you know, discuss with them how we want to proceed and we'll take their feedback. But yeah.

Bucko, Ahmet   7:00
But this is the situation yet.

Inayat, Sajid   7:01
OK. Yeah, yeah.

Peti, Helios   7:03
OK.

Inayat, Sajid   7:06
OK. You know, that was helpful. Thank you.

Bucko, Ahmet   7:09
Yep.

Peti, Helios   7:10
Thank you as well, Said, for your time. Also, you are now. Thank you as well for the explanation.

Bucko, Ahmet   7:12
Anytime. Yep, Yep. Let's if you want, let's keep this chat.

Inayat, Sajid   7:17
Yes, let's keep that.

Bucko, Ahmet   7:18
Yeah, when we can connect and just, you know, meet now and we just continue.

Peti, Helios   7:23
Also, Ahmed, don't mind if I ask, but I'm waiting for the response. If I immediately get the response, I may ping you for any logging issues I may have.

Inayat, Sajid   7:23
Alright, cool.

Bucko, Ahmet   7:31
Of course, yes, yes, sure. Yep. Yep. I I will just be like I will go out for some walking and some grocery shopping. So other than that, I will be at home tonight. So yeah.

Peti, Helios   7:32
Thank you. Thank you very much.
No worries. It's OK. Thank you.

Inayat, Sajid   7:43
Alright, cool. Thanks. Yeah.

Bucko, Ahmet   7:43
Yep, Ben. Yep. See you.

Inayat, Sajid   7:47
Alright, see you guys. Bye now.

Peti, Helios   7:49
See you guys. Say goodbye.

Inayat, Sajid stopped transcription
