Vue.component("footBar", {
  template: `
    <div class="foot">
      <div class="foot-box" :class="{active: activeBtn === 1}" @click="toPage(1)">
        <div class="foot-view"><i class="el-icon-s-home"></i></div>
        <div class="foot-text">Home</div>
      </div>
      
      <div class="foot-box center-btn" @click="toPage(0)">
        <img class="add-btn" src="/imgs/add.png" alt="">
      </div>

      <div class="foot-box" :class="{active: activeBtn === 4}" @click="toPage(4)">
        <div class="foot-view"><i class="el-icon-user"></i></div>
        <div class="foot-text">Profile</div>
      </div>
    </div>
  `,
  data() {
    return {}
  },
  props: ['activeBtn'],
  methods: {
    toPage(i) {
      if (i === 0) {
        location.href = "/blog-edit.html";
      } else if (i === 4) {
        location.href = "/info.html";
      } else if (i === 1) {
        location.href = "/";
      }
    }
  }
});
