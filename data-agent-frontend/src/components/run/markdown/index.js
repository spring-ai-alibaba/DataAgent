import MarkdownAgentContainer from './MarkdownAgentContainer.vue';

const install = function (Vue) {
  Vue.component(MarkdownAgentContainer.name, MarkdownAgentContainer);
};

/* istanbul ignore if */
if (typeof window !== 'undefined' && window.Vue) {
  install(window.Vue);
}

MarkdownAgentContainer.install = install;
export default MarkdownAgentContainer;
