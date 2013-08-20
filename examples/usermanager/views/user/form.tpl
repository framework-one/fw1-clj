<h3>User Info</h3>
<form class="familiar medium" method="post" action="/user/save">
	
	<input type="hidden" name="id" id="id" value="{{user.id}}"/>
	
	<div class="field">
		<label for="first-name" class="label">First Name:</label>
		<input type="text" name="first-name" id="first-name" value="{{user.first-name}}"/>
	</div>
	
	<div class="field">
		<label for="last-name" class="label">Last Name:</label>
		<input type="text" name="last-name" id="last-name" value="{{user.last-name}}"/>
	</div>
	
	<div class="field">
		<label for="email" class="label">Email:</label>
		<input type="text" name="email" id="email" value="{{user.email}}"/>
	</div>
	
	<div class="field">
		<label for="department-id" class="label">Department:</label>
		<select name="department-id" id="department-id">{%
            for dept in departments
            %}
            <option value="{{dept.id}}"{%
                    ifequal user.department-id dept.id
                    %} selected="selected"{%
                    endifequal %}>{{dept.name}}</option>{%
            endfor
            %}
		</select>
	</div>
	
	<div class="control">
		<input type="submit" value="Save User"/>
	</div>
	
</form>
