import classNames from 'classnames';
import PropTypes from 'prop-types';
import React from 'react';
import { UncontrolledTooltip } from 'reactstrap';
import { colorPropType } from '../../../utilities/propTypes';
import TooltipIcon from '../TooltipIcon';
import AdditionalLabel from './AdditionalLabel';
import style from './Input.module.scss';

function CheckBox(
    {
        additionalLabel,
        className,
        color,
        id,
        label,
        tooltip,
        ...props
    },
) {
    return (
        <React.Fragment>
            <div className={classNames(style.formGroup, className, style.checkboxGroup)}>
                <label
                    className={style.checkboxLabel}
                    htmlFor={id}
                >
                    <input
                        type="checkbox"
                        className={style.checkbox}
                        id={id}
                        {...props}
                    />
                    <span
                        className={classNames(style.text, style[color])}
                        id={`checkbox-label-${id}`}
                    >
                        {label}
                        {tooltip && <TooltipIcon />}
                    </span>
                </label>
                <AdditionalLabel title={additionalLabel} />
            </div>
            {tooltip && (
                <UncontrolledTooltip placement="auto" target={`checkbox-label-${id}`}>
                    {tooltip}
                </UncontrolledTooltip>
            )}
        </React.Fragment>
    );
}

CheckBox.propTypes = {
    id: PropTypes.string.isRequired,
    additionalLabel: PropTypes.string,
    label: PropTypes.string,
    className: PropTypes.string,
    color: colorPropType,
    tooltip: PropTypes.string,
};

CheckBox.defaultProps = {
    additionalLabel: undefined,
    label: undefined,
    className: undefined,
    color: undefined,
    tooltip: undefined,
};

export default CheckBox;
