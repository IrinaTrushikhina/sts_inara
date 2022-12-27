# **********************************************************
# сценарий по вводу номера лицевого счета
# **********************************************************
# зависимости от других модулей:
# 1.  общие паттерны - да, нет, согласие, отказ
# require: patterns.sc
#     module = sys.zb-common
# 2. Настройки в модуле chatbot.yaml -  Сколько раз уточняем по номеру ЛС
# injector:
#   AccountInputSettings:
#     MaxRetryCount: 2 
# 3. Настроен обработчик сохранения состояния - в главном модуле 
    # bind("preProcess", function($context) {
    #     $context.session._lastState = $context.currentState;
    #     //$context.session._lastState = $context.contextPath ;
    # });
# 4. Подключен файл AccountNumberInput.js  - запросы к Сервису, информация по ЛС
# 5. Подключен файл GetNumbers.js - вычленяет номер ЛС из найденных сущностей
# require: Functions/GetNumbers.js    
# 6. Добавить интент "подождите", "ГдеНомерЛС", "DontKnow","ЛС_ИнойТипВвода" в CAILA
# 7. Добавить в паттерны цифры
# patterns:
#     $numbers = $regexp<(\d+(-|\/)*)+>

# подключение модуля: 
#    require: AccountInput.sc
#    require: Functions/GetNumbers.js

# !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
# Важно - в телефонном сценарии добавить очистку номера ЛС после окончания сесии (завершили разговор)
# Почему важно - стейт модальный, просто так из него не выйдешь
    # state: HangUp
    #     event!:hangup
    #     event: botHangup
    #     script: FindAccountNumberClear()

# **********************************************************
# Пример использования в стейте:
# ----------------------------------------------------------
#     state: NewAccountMainInfo
#         q: лицев* *
#         if: ($session.Account && $session.Account.Number > 0)
#             a: сейчас дам вам еще информацию по счёту {{$session.Account.Number}}
#             script: 
#                 $reactions.answer(GetAccountNumAnswer($session.Account.Number));
#         elseif: ($session.Account && $session.Account.Number < 0)
#             a: что ж с тобой делать? нет у тебя лицевого счёта ... 
#         else: 
#             a: давайте уточним ваш номер счёта
#             go!:/AccountNumInput/AccountInput
# **********************************************************



require: /Functions/AccountNumberInput.js



theme: /BlockAccountNumInput

    state: AccountInput || modal = true
        script: 
            # log(toPrettyString($request.data.args));
            # log('$session.Account = ' + toPrettyString($session.Account));

            //log( toPrettyString($context.session._lastState) );
            if (($context.session._lastState.substr(1,20)) != "BlockAccountNumInput")
            {
                $session.oldState = $context.session._lastState;  
                FindAccountNumberStart();
                $session.AccountOkState = $request.data.args.okState;
                $session.AccountErrorState = $request.data.args.errorState;
                $session.AccountNoAccounState = $request.data.args.noAccountState;
            }
            $session.Account.MaxRetryCount = $injector.AccountInputSettings.MaxRetryCount || 3;
            $session.Account.RetryAccount = $session.Account.RetryAccount || 0;
            $session.Account.RetryAccount++;
        if: $session.Account.RetryAccount <= $session.Account.MaxRetryCount
            a: Назовите номер вашего лицевого счета
        else: 
            #  уже запрашивали номер ЛС больше 2-х раз. Зафиксировать результат - не смогла Вас понять и вернуть управление в исходный стейт со всеми данными
            script: FindAccountNumberSetResult("DontUnderstand");
            #a: Возвращаюсь назад в {{toPrettyString($session.oldState)}}
            go!: {{$session.AccountErrorState}}
        
        state: AccountInputWait    
            intent: /подождите
            a: да, жду Вас
            script:
               $dialer.setNoInputTimeout(20000); // 20 сек

        state: looser
            q: * $looser *
            q: * $obsceneWord  *
            q: * $stupid  * 
            random: 
                a: Спасибо. Мне крайне важно ваше мнение
                a: Вы очень любезны сегодня
                a: Это комплимент или оскорбление?
            go!: {{$session.contextPath}}
               
        
        state: AccountInputNotNumbersWay
            intent: /ЛС_ИнойТипВвода
            a: Сейчас я умею понимать только цифры. Вы можете назвать номер счета сейчас?
            state: AccountInputNotNumbersWayYes
                q: $yes
                q: $agree
                script:  
                    $session.Account.RetryAccount--;
                go!: ../..
            
            state: AccountInputNotNumbersWayDecline 
                q: $no
                q: $disagree
                script: 
                    FindAccountNumberSetResult("DontKnow"); 
                go!: {{$session.AccountNoAccounState}}
            
            
        state: AccountInputWhereIsAccount
            intent: /ГдеНомерЛС
            a: Номер отображается в счёте Алсеко сразу **над таблицей**.  Вы можете посмотреть счёт и назвать номер **сейчас**?
            state: AccountInputWhereIsAccountYes
                q: $yes
                q: $agree
                script:  
                    $session.Account.RetryAccount--;
                # a: Ваш лицевой счет {{$session.AccountNumber}}. {{ $session.oldState }}
                go!: ../..
            
            state: AccountInputWhereIsAccountDecline 
                q: $no
                q: $disagree
                event: noMatch
                script: 
                    FindAccountNumberSetResult("DontKnow"); 
                go!: {{$session.AccountNoAccounState}}
        
        state: AccountInputNumber 
            
            # проверяем наличие цифр в запросе. если есть, значит говорит номер лицевого счета
            q: * $numbers *
            q: * @duckling.number *
            script: 
                TrySetNumber(words_to_number($entities));
                # log(new Intl.NumberFormat('ru-RU', { style: 'decimal' }).format(GetTempAccountNumber()));
            a: Номер Вашего лицевого счёта {{AccountTalkNumber(GetTempAccountNumber())}}. Поиск займет время. || bargeInIf = AccountNumDecline 
            a: Подождёте?
            script:
                $reactions.timeout({interval: '1s', targetState: 'FindAccount'});
                $dialer.setNoInputTimeout(1000); // Бот ждёт ответ 1 секунду и начинает искать.
                $dialer.bargeInResponse({
                    //bargeIn: "phrase", // при перебивании бот договаривает текущую фразу до конца, а затем прерывается.
                    bargeIn: "forced", // forced — при перебивании бот прерывается сразу, не договаривая текущую фразу до конца.
                    bargeInTrigger: "interim",
                    noInterruptTime: 1500});
            state: BargeInIntent || noContext = true
                event: bargeInIntent
                script:
                    var bargeInIntentStatus = $dialer.getBargeInIntentStatus();
                    log(bargeInIntentStatus.bargeInIf); // => "beforeHangup"
                    var text = bargeInIntentStatus.text;
                    var res = $nlp.matchPatterns(text,["$no", "$disagree"])
        
                    if (res) {
                        $dialer.bargeInInterrupt(true);
                    }
            
            state: AccountInputNumberYes
                q: $yes
                q: $agree
                event: speechNotRecognized
                event: noMatch
                go!: ../FindAccount

            state: AccountInputNumberNo
                q: $no
                q: $disagree
                script: 
                    FindAccountNumberSetResult("AddressCancel"); 
                if: $session.Account.RetryAccount < $session.Account.MaxRetryCount
                    a: Давайте еще раз проверим
                go!: /BlockAccountNumInput/AccountInput

            state: FindAccount
                script: 
                    TrySetNumber(GetTempAccountNumber());

                    FindAccountAddress().then(function(res){
                        //log(toPrettyString(res));
                        if (res && res.count > 0){
                            //log(res.data[0].address_full_name);
                            $session.Account.Address = res.data[0].address_full_name;
                            $reactions.transition('../AccountAddressConfirm')
                            $session.Account.AddressRepeatCount = 0;
                        }else {
                            $session.Account.Address = "";
                            $reactions.transition('../AccountNotFound');
                        }
                    }).catch(function err() {
                        $reactions.answer("Что-то сервер барахлит. ");
                        $reactions.transition('../AccountNotFound')
                    });
                        

            state: AccountAddressConfirm
                script:
                    $session.Account.AddressRepeatCount += 1;
                    log('$request = ' + toPrettyString($request));
                a: Ваш адрес {{$session.Account.Address}}. Верно? 

                state: AccountAddressConfirmYes
                    q: $yes
                    q: $agree
                    script:  
                        FindAccountNumberSetSuccees("Address");
                    # a: Ваш лицевой счет {{$session.AccountNumber}}. {{ $session.oldState }}
                    go!: {{$session.AccountOkState}}
                
                state: AccountAddressDecline 
                    q: $no
                    q: $disagree
                    script: 
                        FindAccountNumberSetResult("AddressCancel"); 
                    if: $session.Account.RetryAccount < $session.Account.MaxRetryCount
                        a: Давайте еще раз проверим
                    go!: /BlockAccountNumInput/AccountInput
                
                state: AccountAddressNoMatch
                    event: noMatch || noContext = true
                    event: speechNotRecognized || noContext = true
                    if: $session.Account.AddressRepeatCount < 2
                        a: Я Вас не расслышала. Повторите еще раз.
                        go!: ..
                    else:
                        go!:../AccountAddressDecline

            state: AccountNotFound
                a: Извините, я не нашла Ваш лицевой счёт. 
                if: $session.Account.RetryAccount < $session.Account.MaxRetryCount
                    a: Давайте еще раз проверим
                go!: /BlockAccountNumInput/AccountInput

        state: AccountInputNoNumber
            event: noMatch || noContext = true
            a: Это не похоже на номер лицевого счета.
            go!: ..
    
    state: DontKnow
        intent: /DontKnow || fromState = "/BlockAccountNumInput/AccountInput"
        script:FindAccountNumberSetResult("DontKnow"); 
        # a: Возвращаю управление в стейт {{$session.oldState}}
        go!: {{$session.AccountNoAccounState}}