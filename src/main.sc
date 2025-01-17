require: slotfilling/slotFilling.sc
  module = sys.zb-common
  
require: patterns.sc
  module = sys.zb-common
require: dateTime/dateTime.sc
  module = sys.zb-common  

require: Functions/GetNumbers.js
require: Functions/AccountsSuppliers.js

# логирование произошедших ошибок
# require: ErrorBind/ErrorLogger.js

#########################################
# ПОДКЛЮЧЕНИЕ ДОПОЛНИТЕЛЬНЫХ СЦЕНАРИЕВ
# сценарий смена собственника
require: ChangeAccountPerson.sc
# сценарий смена количества проживающих
require: ChangeAccountPersonCount.sc
# сценарии по платежам
require: PaymentTotal.sc

#########################################
# Справочник - основные поставщики
require: dicts/MainSuppl.csv
    name = MainSuppl
    var = $MainSuppl

require: CommonAnswers.yaml
    var = CommonAnswers

patterns:
    $Yes_for_contacts = (сейчас/*диктуй*/говори*/давай*)
    $No_for_contacts = (самостоятельно/сам/посмотр* сам/найд* сам)
    $Offline = (оффлайн/лично/офлайн/*жив*/offline/ofline/*офис*)
    $Online = (онлайн/*интернет*/online/электрон*)
    $numbers = $regexp<(\d+(-|\/)*)+>
    $mainSuppl = $entity<MainSuppl> || converter = mainSupplConverter
    $changeOwner = [приобрел* @Недвижимость] *мени* (собственника|хозяина|имя|фамилию)
    

init:
    bind("preProcess", function($context) {
        $context.session._lastState = $context.currentState;
        //$context.session._lastState = $context.contextPath ;
    });
    bind("postProcess", function($context) {
        $context.session.lastState = $context.currentState;
        //$context.session._lastState = $context.currentState;
        // log("**********" + toPrettyString($context.currentState));
        $context.session.AnswerCnt = $context.session.AnswerCnt || 0;
        if (!$context.session.lastState.startsWith("/speechNotRecognizedGlobal"))
            $context.session.AnswerCnt += 1;
        
        //$context.session._lastState = $context.contextPath ;
        // добавляю логи всех ответов бота
        /*
        if ($context.response.replies) {
            $context.response.replies.forEach(function(reply) {
                if (reply.type === "text") {
                    if (reply.text.match(/\[|\]/g) && reply.text.match(/\(|\)/g)) {
                        log("Bot: " + formatLink(reply.text));
                    } else {
                        log("Bot: " + reply.text);
                    }
                }
            });
        }*/        
    });
    ///ChangeAccountPerson/ChangeAccountPerson
    bind("selectNLUResult", 
    function($context) {
        # log("$context.nluResults"  + toPrettyString( $context.nluResults) );
        // если состояние по "clazz":"/NoMatch" - то оставляем приоритет 
        if (
                ($context.nluResults.intents.length > 0) && 
                ($context.nluResults.intents[0].score > 0.45) && 
                $context.nluResults.intents[0].clazz &&
                ($context.nluResults.intents[0].clazz != "/NoMatch")
            ) {
            $context.nluResults.selected = $context.nluResults.intents[0];
            
            # log("$context.nluResults.selected"  + toPrettyString( $context.nluResults.selected) );
            
            return;
        }
        // паттерн TotalPay должен иметь минимальный вес среди всех интентов
        if  ($context.nluResults.selected.clazz == "/PaymentTotal/PaymentQuestion" &&
            $context.nluResults.selected.ruleType == "pattern"){
            if (
                    ($context.nluResults.intents.length > 0) && 
                    # ($context.nluResults.intents[0].score > 0.45) && 
                    $context.nluResults.intents[0].clazz &&
                    ($context.nluResults.intents[0].clazz != "/NoMatch")
                ) {
                $context.nluResults.selected = $context.nluResults.intents[0];
                # log("$context.nluResults.selected TotalPayReplace = "  + toPrettyString( $context.nluResults.selected) );
                
                return;
            }
        }

    }
    );
    # bind("selectNLUResult", function($context) {
    #     // Получим все результаты от всех классификаторов в виде массива.
    #     var allResults = _.chain($context.nluResults)
    #         .omit("selected")
    #         .values()
    #         .flatten()
    #         .value();
    
    #     // Сосчитаем максимальное значение `score` среди всех результатов.
    #     var maxScore = _.chain(allResults)
    #         .pluck("score")
    #         .max()
    #         .value();
    
    #     // Запишем в `nluResults.selected` результат с максимальным весом.
    #     $context.nluResults.selected = _.findWhere(allResults, {
    #         score: maxScore
    #     });
    #     log(toPrettyString($context.nluResults.selected));
    # });
    

    $global.mainSupplConverter = function($parseTree){
        var id = $parseTree.MainSuppl[0].value;
        return $MainSuppl[id].value;
    }
    

theme: /

    state: Start
        q!: $regex</start>
        script:
            $context.session.AnswerCnt = 0;
        # a: Я Инара, ваш виртуальный помощник. Я могу рассказать, как поменять фамилию или количество человек в квитанции, подсказать дату последней оплаты или подсказать контакты поставщика услуг
        a: Я Инара, ваш виртуальный помощник. Я могу рассказать, как поменять фамилию или количество человек в квитанции, 
        a: подсказать дату последней оплаты или 
        a: контакты поставщика услуг
        script:
            $temp.index = $reactions.random(CommonAnswers.WhatDoYouWant.length);
        a: {{CommonAnswers.WhatDoYouWant[$temp.index]}}
        script:
            if ($dialer.getCaller())
                $analytics.setSessionData("Телефон", $dialer.getCaller());
            $dialer.bargeInResponse({
                bargeIn: "phrase",
                bargeInTrigger: "final",
                noInterruptTime: 0});
             FindAccountNumberClear();

    state: Hello
        intent!: /привет
        random:
            a: Здравствуйте!
            a: Алло, я Вас слушаю
    
    state: WhatDoYouWant
        script:
            $temp.index = $reactions.random(CommonAnswers.WhatDoYouWant.length);
        a: {{CommonAnswers.WhatDoYouWant[$temp.index]}}

    state: NoMatch || noContext = true
        event!: noMatch
        # a: Я не понял. Вы сказали: {{$request.query}}
        script:
            $session.catchAll = $session.catchAll || {};
        
            //Начинаем считать попадания в кэчол с нуля, когда предыдущий стейт не кэчол.
            if ($session.lastState && !$session.lastState.startsWith("/CatchAll")) {
                $session.catchAll.repetition = 0;
            } else{
                $session.catchAll.repetition = $session.catchAll.repetition || 0;
            }
            $session.catchAll.repetition += 1;
        if: $context.session.AnswerCnt == 1
            script:
                $temp.index = $reactions.random(CommonAnswers.NoMatch.answers.length);
            a: {{CommonAnswers.NoMatch.answers[$temp.index]}}
            # random:
            #     a: Извините, я Вас не поняла. Повторите пожалуйста 
            #     a: Я Вас не поняла. Сформулируйте по-другому 
            #     a: Скажите еще раз 
            #     a: Мне плохо слышно, повторите
        else:
            a: Для решения Вашего вопроса перевожу Вас на оператора. Пожалуйста, подождите
            go!: /CallTheOperator
    
    state: SwitchToOperator
        q!: перевод на оператора
        q!: $switchToOperator
        intent!: /CallTheOperator
        if: $context.session.AnswerCnt == 1
            a: Чтобы я переключила Вас на нужного оператора, озвучьте свой вопрос
        else:
            a: Переключаю Вас на оператора. Пожалуйста, подождите
            go!: /CallTheOperator
        

    # перевод на оператора.
    # В А Ж Н О: слова перед переводом говорит стейт, который вызывает этот переход
    state: CallTheOperator
        # a: Перевожу Вас на оператора
        script:
            # Александр Цепелев:
            # Привет. Кто-то делал перевод звонка на оператора с подставлением номера абонента? Как вы в поле FROM передавали этот номер?
            
            # Anatoly Belov:
            # у нас работает так:
            
            # var switchReply = {type:"switch"};
            # switchReply.phoneNumber = "ТУТВНУТРЕННИЙНОМЕР";
            # var callerIdHeader = "\""+$dialer.getCaller()+"\""+" <sip:"+$dialer.getCaller()+"@ТУТВНУТРIP>";
            # switchReply.headers = { "P-Asserted-Identity":  callerIdHeader};
            # $response.replies = $response.replies || [];
            # $response.replies.push(switchReply);
            
            # если звонок передается внутри АТС, т все ок )            
            var switchReply = {type:"switch"};
            switchReply.phoneNumber = "4606"; // номер, на который переключаем
            var callerIdHeader = "\""+ $dialer.getCaller() +"\""+" <sip:"+$dialer.getCaller()+"@92.46.54.218>"; // последнеее - внутренний IP 
            //
            switchReply.headers = { "P-Asserted-Identity":  callerIdHeader, testheader: "header"};
            
            // при true, абонент будет возвращен к диалогу с ботом после разговора с оператором, а также, если оператор недоступен.
            switchReply.continueCall = true; 

            // при true, разговор продолжает записываться, в том числе с оператором и при повторном возвращении абонента в диалог с ботом. Запись звонка будет доступна в логах диалогов.
            switchReply.continueRecording = true; 
            
            $response.replies = $response.replies || [];
            $response.replies.push(switchReply);
            

    state: CallTheOperatorTransferEvent
        event: transfer
        script:
            var status = $dialer.getTransferStatus();
            //log(status);
        if: $dialer.getTransferStatus().status === 'FAIL'
            a: К сожалению, на данный момент все операторы заняты. Могу ли я Вам еще чем-то помочь? 
        # else:
        #     a: Спасибо, что связались с нами. Оцените, пожалуйста, качество обслуживания.    
        state: CanIHelpYouAgree
            q: $yes
            q: $agree
            intent: /Согласие
            go!: /WhatDoYouWant
            
        state: CanIHelpYouDisagree
            q: $no
            q: $disagree
            intent: /Несогласие
            go!: /bye                

    state: repeat || noContext = true
        q!:  ( повтор* / что / еще раз* / ещё раз*)
        go!: {{$session.contextPath}}
    # go!: {{ $context.session._lastState }} 

    state: bye
        q!: $bye
        intent!: /Прощание
        a: Благодарим за обращение!
        random: 
            a: До свидания!
            a: Надеюсь, я смогла вам помочь. Удачи!
        script:
            $dialer.hangUp();
            
    state: greeting
        intent: /greeting
        random: 
            a: Пожалуйста || htmlEnabled = false, html = "Пожалуйста"
            a: Не за что || htmlEnabled = false, html = "Не за что"
            a: Я старалась || htmlEnabled = false, html = "Я старалась"
            
    state: looser
        q!: * $looser *
        q!: * $obsceneWord *
        q!: * $stupid  * 
        random: 
            a: Спасибо. Мне крайне важно ваше мнение
            a: Вы очень любезны сегодня
            a: Это комплимент или оскорбление?
        script:
            $analytics.setMessageLabel("Отрицательная")
            # здесь хочется Чем я могу Вам помочь? Иначе провисание диалога

    state: HangUp
        event!: hangup
        event!: botHangup
        script: FindAccountNumberClear()

    state: WhereAreYou || noContext = true
        q!: где ты [сейчас]
        a: {{$context.contextPath}}
        #a: {{$context.session._lastState}}            

    state: ClearAccount
        q!: сбрось лицев* 
        script: FindAccountNumberClear();
        #a: Ок

    state: speechNotRecognizedGlobal
        event!: speechNotRecognized
        script:
            $session.speechNotRecognized = $session.speechNotRecognized || {};
            //Начинаем считать попадания в кэчол с нуля, когда предыдущий стейт не кэчол.
            if ($session.lastState && !$session.lastState.startsWith("/speechNotRecognizedGlobal")) {
                $session.speechNotRecognized.repetition = 0;
            } else{
                $session.speechNotRecognized.repetition = $session.speechNotRecognized.repetition || 0;
            }
            $session.speechNotRecognized.repetition += 1;
            
        if: $session.speechNotRecognized.repetition >= 3
            a: Кажется, проблемы со связью.
            script:
                $dialer.hangUp();
        else:
            random: 
                a: Извините, я не расслышала. Повторите, пожалуйста.
                a: Не совсем поняла. Можете повторить, пожалуйста?
                a: Повторите, пожалуйста. Вас не слышно.

theme: /ИнициацияЗавершения
    
    state: CanIHelpYou 
        script:
            $temp.index = $reactions.random(CommonAnswers.CanIHelpYou.length);
        a: {{CommonAnswers.CanIHelpYou[$temp.index]}}
        
        state: CanIHelpYouAgree
            q: $yes
            q: $agree
            intent: /Согласие
            intent: /Согласие_помочь
            go!: /WhatDoYouWant
            
        state: CanIHelpYouDisagree
            q: $no
            q: $disagree
            intent: /Несогласие
            intent: /Несогласие_помочь
            go!: /bye

theme: /SupplierContacts
    state: SupplierContacts
        intent!: /КонтактыПоставщика
        script: 
        # если есть услуга, то выделяем ее 
            log($parseTree);
            if ($parseTree._Услуга){
                $session.Serv = $parseTree._Услуга.SERV_ID;
            }
        a: даем контакты по услуге
        #  если есть ЛС, то смотрим по нему. если ЛС нет, то надо спрашивать
                    # смотрим, был ли лицевой счет выявлен в ходе диалога
        if: ($session.Account && $session.Account.Number > 0)
            # Есть номер лицевого счета, будем давать информацию по нему по контактам поставщиков
            go!: SupplierContactsByAccountServ
        else: 
            # здесь идет определение, что ЛС в рамках дилагога еще не запрашивался - передаем управление туда
            a: Чтобы я дала контакты нужных Вам поставщиков, нужен Ваш лицевой счёт
            BlockAccountNumber:
                okState = SupplierContactsByAccountServ
                errorState = SupplierContactsError
                noAccountState = SupplierContactsError
            
        state: SupplierContactsError
            a: нет лицевого - нет контактов
        
        state: SupplierContactsByAccountServ
            a: ЛС {{AccountTalkNumber($session.Account.Number)}}, услуга {{$session.Serv}}
    
        
        