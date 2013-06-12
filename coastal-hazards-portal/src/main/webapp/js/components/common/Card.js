CCH.Objects.Card = function(args) {
	CCH.LOG.info('Card.js::constructor:Card class is initializing.');
	var me = (this === window) ? {} : this;

	me.item = args.item;
	me.bbox = me.item.bbox;
	me.type = me.item.type;
	me.summary = me.item.summary;
	me.name = me.item.name;
	me.attr = me.item.attr;
	me.service = me.item.service;
	me.htmlEntity = null;
	me.pinButton = null;
	me.tweetButton = null;

	return $.extend(me, {
		create: function() {
			me.container = $('<div />').addClass('description-container container-fluid');
			var titleRow = $('<div />').addClass('description-title-row row-fluid');
			var descriptionRow = $('<div />').addClass('description-description-row row-fluid');
			me.pinButton = $('<button />').addClass('btn  span1').attr('type', 'button').append($('<i />').addClass('slide-menu-icon-zoom-in icon-eye-open slide-button muted'));
			me.tweetButton = $('<button />').addClass('btn  span1').attr('type', 'button').append($('<i />').addClass('slide-menu-icon-twitter icon-twitter slide-button muted'));

			[me.pinButton, me.tweetButton].each(function(button) {
				button.on({
					'mouseover': function(evt) {
						$(this).find('i').removeClass('muted');
					},
					'mouseout': function(evt) {
						$(this).find('i').addClass('muted');
					}
				});
			});

			me.tweetButton.on({
				'click': function(evt) {
					$(me).trigger('card-button-tweet-clicked');
				}
			});

			me.pinButton.on({
				'click': function() {
					$(me).trigger('card-button-pin-clicked');
				}
			});

			if (me.type === 'storms') {
				me.container.addClass('description-container-storms');
			} else if (me.type === 'vulnerability') {
				me.container.addClass('description-container-vulnerability');
			} else {
				me.container.addClass('description-container-historical');
			}

			var titleColumn = $('<span />').addClass('description-title span10').html(me.name);

			titleRow.append(me.pinButton, titleColumn, me.tweetButton);

			// TODO description should come from summary service (URL in item)
			descriptionRow.append($('<p />').addClass('slide-vertical-description unselectable').html(me.summary.medium));

			me.container.append(titleRow, descriptionRow);
			if (CCH.ui.currentSizing === 'large') {
				me.container.addClass('description-container-large');
			} else if (CCH.ui.currentSizing === 'small') {
				me.container.addClass('description-container-small');
			}

			me.container.data('card', me);
			return me.container;
		}
	});
};