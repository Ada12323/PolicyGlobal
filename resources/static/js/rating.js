const toggleInput = document.getElementById('toggleInput');//整体评分功能div
const ratingInput = document.getElementById('ratingInput');//评分输入框
const alreadyRated = document.getElementById('alreadyRated');//已经评过分的提示(div)
const average=document.getElementById('average');//平均分(div)
const id = parseId();  //政策ID

// 验证评分输入
function validateRating(input) {
    const value = parseFloat(input.value);
    // 检查是否为数字且在1-10范围内
    if (isNaN(value) || value < 1 || value > 10) {
        input.setCustomValidity('请输入1-10的数字');
        input.reportValidity();
        return false;
    } else {
        input.setCustomValidity('');
        return true;
    }
}

// 实时验证输入
ratingInput.addEventListener('input', function() {
    validateRating(this);
});

// 添加HTML5验证属性
ratingInput.setAttribute('type', 'number');
ratingInput.setAttribute('min', '1');
ratingInput.setAttribute('max', '10');
ratingInput.setAttribute('step', '0.1'); // 允许小数评分，如8.5
ratingInput.setAttribute('placeholder', '1-10分');

$('#toggleBtn').click(function () {
    $.ajax({
        url: "api/searchrating",
        type: "GET",
        data: {
            pid: id,
        },
        success: function (data) {
            if(data==null){
                toggleInput.classList.toggle('hidden');
            }else{
                alreadyRated.classList.toggle('hidden');
                alreadyRated.innerHTML = `<span>您已经为该政策评分，当前的评分为${data}分</span>`;
            }
        }
    });
});

$('#submitRating').click(function () {
    // 提交前再次验证
    if (!validateRating(ratingInput)) {
        return;
    }

    // 确保评分在1-10范围内
    const ratingValue = parseFloat(ratingInput.value);
    if (ratingValue < 1 || ratingValue > 10) {
        alert('评分必须在1-10分之间');
        return;
    }

    $.ajax({
        url: "api/addrating",
        type: "POST",
        dataType: "json",
        data: {
            pid: id,
            rating: ratingValue
        },
        success: function (data) {
            if(data>0){
                toggleInput.classList.toggle('hidden');
                alreadyRated.classList.toggle('hidden');
                alreadyRated.innerHTML = `<span>您已经为该政策评分，当前的评分为${ratingValue}分</span>`;
                //发送请求获取平均分
                $.ajax({
                    url: "api/avgrating",
                    type: "GET",
                    dataType: "json",
                    data: {
                        pid: id,
                    },
                    success: function (data) {
                        if(data!=null){
                            average.innerHTML = `<span style="color: #1d4ed8">平均分：${data.avg}分, 评分人数：${data.count}人</span>`;
                        }
                    }
                });
            }else {
                toggleInput.classList.toggle('hidden');
                alert('评分失败，请重试');
            }
        },
        error: function() {
            alert('评分失败，请重试');
        }
    });
});

$(document).ready(function () {
    $.ajax({
        url: "api/avgrating",
        type: "GET",
        dataType: "json",
        data: {
            pid: id,
        },
        success: function (data) {
            if(data!=null){
                average.innerHTML = `<span style="color: #1d4ed8">平均分：${data.avg}分, 评分人数：${data.count}人</span>`;
            }
        }
    });
});