import { ref } from 'vue';

export function useTypingBuffer(typingSpeed = 50) {
    const displayedText = ref('');
    const isTyping = ref(false);
    const isFinished = ref(false);

    let queue = [];
    let intervalId = null;

    // 백엔드에서 받은 텍스트 조각(Chunk)을 큐에 추가
    const addChunk = (textChunk) => {
        // HTML 태그(<br>, <b> 등)가 끊기지 않도록 정규식으로 통째로 분리하여 큐에 삽입
        // 일반 글자는 한 글자씩 쪼개짐
        const tokens = textChunk.match(/(<[^>]+>|[^<])/g) || [];
        queue.push(...tokens);

        // 타자기가 멈춰있다면 다시 작동
        if (!isTyping.value) {
            startTyping();
        }
    };

    // 큐에서 글자를 하나씩 빼서 화면에 출력하는 핵심 로직
    const startTyping = () => {
        isTyping.value = true;
        isFinished.value = false;

        intervalId = setInterval(() => {
            if (queue.length > 0) {
                displayedText.value += queue.shift();
            }
            // 큐가 비었더라도 SSE 통신이 안 끝났을 수 있으므로 대기
        }, typingSpeed);
    };

    // 백엔드에서 모든 데이터 전송이 완료되었다는 신호를 받았을 때 호출
    const finishTyping = () => {
        // 큐에 남아있는 텍스트가 다 출력될 때까지 기다렸다가 종료 처리
        const checkEmpty = setInterval(() => {
            if (queue.length === 0) {
                clearInterval(checkEmpty);
                clearInterval(intervalId);
                isTyping.value = false;
                isFinished.value = true;
            }
        }, typingSpeed);
    };

    // 플레이어가 스킵 버튼을 눌렀을 때 남은 텍스트를 한 번에 출력
    const skipTyping = () => {
        clearInterval(intervalId);
        displayedText.value += queue.join('');
        queue = [];
        isTyping.value = false;
        isFinished.value = true;
    };

    // 초기화 (재사용 시)
    const reset = () => {
        clearInterval(intervalId);
        displayedText.value = '';
        queue = [];
        isTyping.value = false;
        isFinished.value = false;
    };

    return {
        displayedText,
        isTyping,
        isFinished,
        addChunk,
        finishTyping,
        skipTyping,
        reset
    };
}